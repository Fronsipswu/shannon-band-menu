/* SBM control client. The metrics stream remains receive-only. */
static char broker_session[4096];

static int broker_rpc(int fd, const char *request, char *reply, size_t cap) {
    if (write_all(fd, (const uint8_t *)request, strlen(request)))
        return -1;
    size_t used = 0;
    int64_t deadline = monotonic_us() + 8000000;
    while (used + 1 < cap && !stopping) {
        int64_t remaining = deadline - monotonic_us();
        if (remaining <= 0) {
            errno = ETIMEDOUT;
            return -1;
        }
        struct pollfd p = {fd, POLLIN, 0};
        int ready = poll(&p, 1, (int)((remaining + 999) / 1000));
        if (ready < 0 && errno == EINTR)
            continue;
        if (ready <= 0) {
            errno = ETIMEDOUT;
            return -1;
        }
        ssize_t n = read(fd, reply + used, 1);
        if (n <= 0) {
            errno = ECONNRESET;
            return -1;
        }
        if (reply[used] == '\n') {
            reply[used] = 0;
            return 0;
        }
        used++;
    }
    errno = EPROTO;
    return -1;
}
static int broker_string(const char *json, const char *key, char *out, size_t cap) {
    char name[64];
    snprintf(name, sizeof(name), "\"%s\":\"", key);
    const char *p = strstr(json, name);
    if (!p)
        return -1;
    p += strlen(name);
    size_t n = 0;
    while (*p && *p != '\"') {
        if (*p == '\\' || (unsigned char)*p < 32 || n + 1 >= cap)
            return -1;
        out[n++] = *p++;
    }
    if (*p != '\"')
        return -1;
    out[n] = 0;
    return 0;
}
static int broker_begin(const char *control_path, int *control_fd, int *stream_fd) {
    *control_fd = *stream_fd = -1;
    int fd = connect_socket(control_path);
    if (fd < 0)
        return (errno == ENOENT || errno == ECONNREFUSED) ? 1 : -1;
    struct ucred cred;
    socklen_t length = sizeof(cred);
    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &cred, &length) || cred.uid != geteuid()) {
        close(fd);
        errno = EACCES;
        return -1;
    }
    char reply[4096], tap[108];
    if (broker_rpc(fd, "{\"cmd\":\"hello\"}\n", reply, sizeof(reply))) {
        close(fd);
        return -1;
    }
    if (!strstr(reply, "\"ok\":true") || !strstr(reply, "\"protocol\":1,") ||
        broker_string(reply, "tap_path", tap, sizeof(tap))) {
        fprintf(stderr, "Incompatible broker: %s\n", reply);
        close(fd);
        errno = EPROTO;
        return -1;
    }
    int stream = connect_socket(tap);
    if (stream < 0) {
        close(fd);
        return -1;
    }
    if (broker_rpc(fd, "{\"cmd\":\"open\",\"profile\":\"sbm-kpi\"}\n", reply, sizeof(reply))) {
        close(stream);
        close(fd);
        return -1;
    }
    if (!strstr(reply, "\"ok\":true")) {
        fprintf(stderr, "Broker refused session: %s\n", reply);
        close(stream);
        close(fd);
        errno = EBUSY;
        return -1;
    }
    snprintf(broker_session, sizeof(broker_session), "%s", reply);
    printf("{\"type\":\"session\",\"transport\":\"broker\",\"state\":%s}\n", reply);
    fflush(stdout);
    *control_fd = fd;
    *stream_fd = stream;
    return 0;
}
static void broker_end(int fd) {
    if (fd < 0)
        return;
    if (!stopping) {
        char reply[4096];
        (void)broker_rpc(fd, "{\"cmd\":\"close\"}\n", reply, sizeof(reply));
    }
    close(fd); /* EOF also releases only this client's lease. */
}
static int broker_status(const char *path) {
    int fd = connect_socket(path);
    if (fd < 0)
        return -1;
    struct ucred cred;
    socklen_t size = sizeof(cred);
    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &cred, &size) || cred.uid != geteuid()) {
        close(fd);
        errno = EACCES;
        return -1;
    }
    char reply[4096];
    int result = broker_rpc(fd, "{\"cmd\":\"status\"}\n", reply, sizeof(reply));
    if (!result)
        puts(reply);
    close(fd);
    return result;
}
