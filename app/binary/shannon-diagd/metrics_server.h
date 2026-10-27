/* App-facing stats are separate from the root-only broker control/data API. */
#define METRICS_REPLY_SIZE 65536
#define METRICS_WATCHDOG_US 15000000

typedef struct {
    int listener;
    int client;
    uid_t app_uid;
    int64_t last_request;
    int64_t published_at;
    char input[256];
    size_t input_size;
    char output[METRICS_REPLY_SIZE];
    size_t output_size;
    size_t output_sent;
    char *latest;
    const char *transport;
    const char *session;
    int stop_after_reply;
} MetricsServer;

static void metrics_disconnect(MetricsServer *server) {
    if (server->client >= 0)
        close(server->client);
    server->client = -1;
    server->input_size = 0;
    server->output_size = 0;
    server->output_sent = 0;
}

/* Binding first prevents a duplicate app helper from activating the modem. */
static int metrics_open(MetricsServer *server, uid_t app_uid) {
    memset(server, 0, sizeof(*server));
    server->listener = server->client = -1;
    server->app_uid = app_uid;
    server->transport = "starting";
    server->session = "null";
    server->last_request = monotonic_us();
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    int length = snprintf(address.sun_path + 1, sizeof(address.sun_path) - 1, "sbm_diagd_%u",
                          (unsigned)app_uid);
    if (length < 0 || (size_t)length >= sizeof(address.sun_path) - 1)
        return -1;
    int listener = socket(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK | SOCK_CLOEXEC, 0);
    if (listener < 0)
        return -1;
    socklen_t size = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + length);
    if (bind(listener, (struct sockaddr *)&address, size) || listen(listener, 2)) {
        int error = errno;
        close(listener);
        errno = error;
        return -1;
    }
    server->listener = listener;
    return 0;
}

/* The decoder's callback preserves the existing schema and completed windows. */
static void metrics_publish(Telemetry *telemetry, int64_t second, void *opaque) {
    MetricsServer *server = opaque;
    char *json = NULL;
    size_t size = 0;
    FILE *stream = open_memstream(&json, &size);
    if (!stream)
        return;
    telemetry_json(telemetry, second, stream);
    int failed = ferror(stream);
    if (fclose(stream) || failed || size >= METRICS_REPLY_SIZE - 5000) {
        free(json);
        return;
    }
    /* The snapshot is embedded in one NDJSON response, not its own line. */
    while (size && (json[size - 1] == '\n' || json[size - 1] == '\r'))
        json[--size] = 0;
    free(server->latest);
    server->latest = json;
    server->published_at = monotonic_us();
}

static void metrics_poll(MetricsServer *server, struct pollfd *fds) {
    fds[0] = (struct pollfd){server->listener, POLLIN, 0};
    fds[1] = (struct pollfd){server->client, (short)(server->output_size ? POLLOUT : POLLIN), 0};
}

/* This intentionally small protocol accepts no transport or modem commands. */
static void metrics_request(MetricsServer *server, const char *request) {
    int stats = !strcmp(request, "{\"cmd\":\"stats\"}");
    int stop = !strcmp(request, "{\"cmd\":\"stop\"}");
    int size;
    if (!stats && !stop) {
        size = snprintf(server->output, sizeof(server->output),
                        "{\"ok\":false,\"error\":\"unknown_command\"}\n");
    } else if (stop) {
        fprintf(stderr, "App requested DIAG Stop.\n");
        server->stop_after_reply = 1;
        size = snprintf(server->output, sizeof(server->output), "{\"ok\":true}\n");
    } else {
        server->last_request = monotonic_us();
        int64_t age_ms = server->latest ? (monotonic_us() - server->published_at) / 1000 : -1;
        const char *snapshot = server->latest && age_ms <= 3000 ? server->latest : "null";
        size = snprintf(server->output, sizeof(server->output),
                        "{\"ok\":true,\"version\":\"%s\",\"transport\":\"%s\","
                        "\"session\":%s,\"age_ms\":%lld,\"snapshot\":%s}\n",
                        VERSION, server->transport, server->session, (long long)age_ms, snapshot);
    }
    if (size < 0 || (size_t)size >= sizeof(server->output)) {
        metrics_disconnect(server);
        return;
    }
    server->output_size = (size_t)size;
    server->output_sent = 0;
}

static void metrics_process(MetricsServer *server, struct pollfd *fds) {
    if (fds[0].revents & POLLIN) {
        int client = accept4(server->listener, NULL, NULL, SOCK_NONBLOCK | SOCK_CLOEXEC);
        if (client >= 0) {
            struct ucred peer;
            socklen_t size = sizeof(peer);
            if (getsockopt(client, SOL_SOCKET, SO_PEERCRED, &peer, &size) ||
                (peer.uid != server->app_uid && peer.uid != 0) || server->client >= 0) {
                fprintf(stderr, "Rejected app connection (existing_client=%d).\n",
                        server->client >= 0);
                close(client);
            } else {
                server->client = client;
            }
        }
    }
    if (server->client < 0 || server->client != fds[1].fd)
        return;
    short events = fds[1].revents;
    if (events & (POLLHUP | POLLERR | POLLNVAL)) {
        fprintf(stderr, "App socket closed (poll_events=0x%x).\n", events);
        metrics_disconnect(server);
        return;
    }
    if ((events & POLLIN) && !server->output_size) {
        char byte;
        /* One request at a time leaves a pipelined request in the socket. */
        for (unsigned count = 0; count < sizeof(server->input); count++) {
            ssize_t received = recv(server->client, &byte, 1, MSG_DONTWAIT);
            if (received <= 0) {
                if (!received || (errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR)) {
                    metrics_disconnect(server);
                }
                break;
            }
            if (byte == '\n') {
                server->input[server->input_size] = 0;
                metrics_request(server, server->input);
                server->input_size = 0;
                break;
            }
            if (!byte || server->input_size + 1 >= sizeof(server->input)) {
                metrics_disconnect(server);
                break;
            }
            server->input[server->input_size++] = byte;
        }
    }
    if ((events & POLLOUT) && server->output_size) {
        ssize_t sent = send(server->client, server->output + server->output_sent,
                            server->output_size - server->output_sent, MSG_NOSIGNAL);
        if (sent > 0) {
            server->output_sent += (size_t)sent;
            if (server->output_sent == server->output_size) {
                server->output_size = server->output_sent = 0;
                if (server->stop_after_reply)
                    stopping = 1;
            }
        } else if (!sent || (errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR)) {
            metrics_disconnect(server);
        }
    }
}

static void metrics_close(MetricsServer *server) {
    metrics_disconnect(server);
    if (server->listener >= 0)
        close(server->listener);
    free(server->latest);
}
