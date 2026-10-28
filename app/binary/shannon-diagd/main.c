#define _GNU_SOURCE
#include "telemetry.h"
#include "rrc/shannon_rrc.h"
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <poll.h>
#include <signal.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#define VERSION "0.4.4"
#define MAX_FRAME 65537
static volatile sig_atomic_t stopping;
static void stop_signal(int sig) {
    (void)sig;
    stopping = 1;
}
static int64_t monotonic_us(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts))
        return -1;
    return (int64_t)ts.tv_sec * 1000000 + ts.tv_nsec / 1000;
}
static int write_all(int fd, const uint8_t *p, size_t n) {
    while (n && !stopping) {
        ssize_t sent = write(fd, p, n);
        if (sent < 0 && errno == EINTR)
            continue;
        if (sent < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
            struct pollfd f = {fd, POLLOUT, 0};
            if (poll(&f, 1, 1000) > 0)
                continue;
        }
        if (sent <= 0)
            return -1;
        p += (size_t)sent;
        n -= (size_t)sent;
    }
    return n ? -1 : 0;
}
static int activate(int fd) {
    static const uint8_t start[] = {0x7f, 0x12, 0, 0, 0x0f, 0, 0, 0, 0xa0, 0,
                                    0x52, 0,    0, 0, 0,    1, 0, 0, 0,    0x7e};
    static const uint8_t query[] = {0x7f, 0x16, 0, 0,    0x13, 0,    0,    0, 0xa0, 0, 0,    0,
                                    0,    0,    0, 0x34, 0xdc, 0x12, 0xfe, 0, 0,    0, 0xc0, 0x7e};
    static const uint8_t select[] = {0x7f, 0x0f, 0, 0, 0x0c, 0, 0,    0,   0xa0,
                                     0,    0x20, 0, 0, 0,    0, 0xff, 0x7e};
    static const uint8_t refresh[] = {0x7f, 0x0f, 0, 0, 0x0c, 0, 0,    0,   0xa0,
                                      0,    0x22, 0, 0, 0,    0, 0xff, 0x7e};
    const uint8_t *packets[] = {start, query, select, refresh};
    size_t sizes[] = {sizeof(start), sizeof(query), sizeof(select), sizeof(refresh)};
    for (int i = 0; i < 4; i++) {
        if (write_all(fd, packets[i], sizes[i]))
            return -1;
        struct timespec pause = {0, 50000000};
        nanosleep(&pause, NULL);
    }
    return 0;
}
static void stop_stream(int fd) {
    static const uint8_t packet[] = {0x7f, 0x12, 0, 0, 0x0f, 0, 0, 0, 0xa0, 0,
                                     0x90, 0,    0, 0, 0,    0, 0, 0, 0,    0x7e};
    /* Only after this process explicitly started a session. */
    ssize_t ignored = write(fd, packet, sizeof(packet));
    (void)ignored;
}
static int busy_owner(const char *path) {
    struct stat target;
    if (stat(path, &target) || !S_ISCHR(target.st_mode))
        return -1;
    DIR *proc = opendir("/proc");
    if (!proc)
        return -1;
    struct dirent *entry;
    int owner = 0, incomplete = 0;
    while ((entry = readdir(proc))) {
        char *end;
        long pid = strtol(entry->d_name, &end, 10);
        if (*end || pid <= 0 || pid == (long)getpid())
            continue;
        char dir[96];
        snprintf(dir, sizeof(dir), "/proc/%ld/fd", pid);
        DIR *fds = opendir(dir);
        if (!fds) {
            if (errno != ENOENT && errno != ESRCH)
                incomplete = 1;
            continue;
        }
        struct dirent *fd;
        while ((fd = readdir(fds))) {
            if (fd->d_name[0] == '.')
                continue;
            char link[384];
            snprintf(link, sizeof(link), "%s/%s", dir, fd->d_name);
            struct stat st;
            if (stat(link, &st)) {
                if (errno != ENOENT && errno != ESRCH)
                    incomplete = 1;
                continue;
            }
            if (S_ISCHR(st.st_mode) && st.st_rdev == target.st_rdev) {
                owner = (int)pid;
                break;
            }
        }
        closedir(fds);
        if (owner)
            break;
    }
    closedir(proc);
    return owner ? owner : (incomplete ? -1 : 0);
}
static int connect_socket(const char *name) {
    struct sockaddr_un sa;
    memset(&sa, 0, sizeof(sa));
    sa.sun_family = AF_UNIX;
    size_t n = strlen(name);
    int abstract = name[0] != '/';
    if (n + abstract >= sizeof(sa.sun_path)) {
        errno = ENAMETOOLONG;
        return -1;
    }
    memcpy(sa.sun_path + abstract, name, n);
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0)
        return -1;
    socklen_t size = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + n + (abstract ? 1 : 1));
    if (connect(fd, (struct sockaddr *)&sa, size)) {
        int e = errno;
        close(fd);
        errno = e;
        return -1;
    }
    return fd;
}
#include "broker_client.h"
#include "metrics_server.h"
static int read_exact(int fd, uint8_t *p, size_t n, int allow_eof) {
    size_t done = 0;
    while (done < n && !stopping) {
        ssize_t got = read(fd, p + done, n - done);
        if (got < 0 && errno == EINTR)
            continue;
        if (got < 0)
            return -1;
        if (!got)
            return (!done && allow_eof) ? 0 : -1;
        done += (size_t)got;
    }
    return stopping ? 0 : 1;
}
static void envelope(Telemetry *s, int64_t us, const uint8_t *p, size_t n) {
    if (n < 11) {
        s->malformed++;
        return;
    }
    if (p[4] != 0xa1) {
        telemetry_advance(s, us);
        return;
    }
    unsigned raw = p[5], stack = raw > 32 ? raw / 32 : 0;
    unsigned id = ((unsigned)p[6] << 8) | (raw - stack * 32);
    telemetry_decode(s, us, read_u16(p), id, stack, p + 11, n - 11);
}
static int replay(Telemetry *s, int fd) {
    uint8_t magic[8];
    if (read_exact(fd, magic, 8, 0) != 1 || memcmp(magic, "SDMRv1\n\0", 8)) {
        fprintf(stderr, "Expected timestamped SDMRv1 records.\n");
        return 1;
    }
    uint8_t header[12], payload[65535];
    int status;
    while (!stopping && (status = read_exact(fd, header, sizeof(header), 1)) == 1) {
        uint64_t ts = (uint64_t)read_u32(header) | ((uint64_t)read_u32(header + 4) << 32);
        uint32_t n = read_u32(header + 8);
        if (ts == UINT64_MAX && n == 0)
            return 0; /* ADB may not forward stdin EOF. */
        if (ts > INT64_MAX || n < 11 || n > sizeof(payload) || read_exact(fd, payload, n, 0) != 1) {
            fprintf(stderr, "Invalid or truncated replay record.\n");
            return 1;
        }
        envelope(s, (int64_t)ts, payload, n);
        if (ferror(stdout))
            return 1;
    }
    if (!stopping && status < 0) {
        fprintf(stderr, "Truncated replay header.\n");
        return 1;
    }
    return 0; /* EOF never promotes the final partial second to a full second */
}
typedef struct {
    uint8_t bytes[MAX_FRAME];
    size_t used;
    uint64_t valid, bytes_read;
} Framer;
static void feed(Telemetry *s, Framer *f, const uint8_t *data, size_t n, int64_t us) {
    f->bytes_read += n;
    while (n--) {
        uint8_t v = *data++;
        if (!f->used && v != 0x7f)
            continue;
        f->bytes[f->used++] = v;
        for (;;) {
            if (f->used < 3)
                break;
            size_t total = (size_t)read_u16(f->bytes + 1) + 2;
            if (total < 16 || total > MAX_FRAME ||
                (f->used >= total && f->bytes[total - 1] != 0x7e)) {
                s->framing_errors++;
                size_t next = 1;
                while (next < f->used && f->bytes[next] != 0x7f)
                    next++;
                memmove(f->bytes, f->bytes + next, f->used - next);
                f->used -= next;
                continue;
            }
            if (f->used < total)
                break;
            const uint8_t *p = f->bytes;
            f->valid++;
            if (p[8] == 0xa1) {
                unsigned raw = p[9], stack = raw > 32 ? raw / 32 : 0,
                         id = ((unsigned)p[10] << 8) | (raw - stack * 32);
                telemetry_decode(s, us, 0, id, stack, p + 15, total - 16);
            }
            memmove(f->bytes, f->bytes + total, f->used - total);
            f->used -= total;
        }
    }
}
static void usage(FILE *f) {
    fprintf(f,
            "shannon-diagd %s — standalone Shannon DM telemetry\n"
            "  --device PATH   direct device (default /dev/umts_dm0; root)\n"
            "  --serve UID     app-UID stats socket with a 15-second watchdog\n"
            "  --auto          broker first; activate directly only if device is free\n"
            "  --broker PATH   require the SBM broker control socket\n"
            "  --broker-status PATH  query broker status without opening a session\n"
            "  --control PATH  override the control socket used by --auto\n"
            "  --socket NAME   experimental raw-framed UNIX socket transport\n"
            "  --input PATH    raw framed input, '-' for stdin, arrival-time accounting\n"
            "  --records PATH timestamped SDMRv1 replay, '-' for stdin\n"
            "  --activate      send known probe/LTE selection; Stop on clean exit\n"
            "  --seconds N     bound live/raw run; default until SIGINT/SIGTERM\n"
            "  --version       print version\n"
            "JSON snapshots go to stdout, status to stderr. No modem NV/AT/menu writes.\n",
            VERSION);
}
int main(int argc, char **argv) {
    atexit(shannon_rrc_reset);
    const char *path = "/dev/umts_dm0", *mode = "device",
               *control_path = "/data/adb/shannon-dm-relay/control.sock";
    int init = 0, control_fd = -1, direct_lock = -1;
    double seconds = 0;
    int status_only = 0;
    long app_uid = -1;
    MetricsServer metrics;
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--help")) {
            usage(stdout);
            return 0;
        }
        if (!strcmp(argv[i], "--version")) {
            puts(VERSION);
            return 0;
        }
        if (!strcmp(argv[i], "--activate")) {
            init = 1;
            continue;
        }
        if (!strcmp(argv[i], "--serve") && i + 1 < argc) {
            char *end;
            app_uid = strtol(argv[++i], &end, 10);
            if (*end || app_uid < 0 || app_uid > INT32_MAX) {
                usage(stderr);
                return 2;
            }
            continue;
        }
        if (!strcmp(argv[i], "--auto")) {
            mode = "auto";
            continue;
        }
        if (!strcmp(argv[i], "--control") && i + 1 < argc) {
            control_path = argv[++i];
            continue;
        }
        if (!strcmp(argv[i], "--broker") && i + 1 < argc) {
            mode = "broker";
            control_path = argv[++i];
            continue;
        }
        if (!strcmp(argv[i], "--broker-status") && i + 1 < argc) {
            mode = "broker";
            status_only = 1;
            control_path = argv[++i];
            continue;
        }
        if (i + 1 < argc && !strcmp(argv[i], "--seconds")) {
            char *end;
            seconds = strtod(argv[++i], &end);
            if (*end || !isfinite(seconds) || seconds <= 0 || seconds > 86400) {
                usage(stderr);
                return 2;
            }
            continue;
        }
        if (i + 1 < argc && (!strcmp(argv[i], "--device") || !strcmp(argv[i], "--socket") ||
                             !strcmp(argv[i], "--records") || !strcmp(argv[i], "--input"))) {
            mode = argv[i] + 2;
            path = argv[++i];
            continue;
        }
        usage(stderr);
        return 2;
    }
    if (init && strcmp(mode, "device") && strcmp(mode, "socket")) {
        fprintf(stderr, "--activate requires a direct device or legacy socket; managed broker "
                        "sessions activate themselves.\n");
        return 2;
    }
    struct sigaction action;
    memset(&action, 0, sizeof(action));
    action.sa_handler = stop_signal;
    sigemptyset(&action.sa_mask);
    sigaction(SIGINT, &action, NULL);
    sigaction(SIGTERM, &action, NULL);
    signal(SIGPIPE, SIG_IGN);
    if (status_only) {
        int rc = broker_status(control_path);
        if (rc)
            perror("broker status");
        return rc ? 1 : 0;
    }
    if (app_uid >= 0 && (status_only || !strcmp(mode, "records"))) {
        usage(stderr);
        return 2;
    }
    if (app_uid >= 0 && metrics_open(&metrics, (uid_t)app_uid)) {
        perror("metrics socket");
        return 1;
    }
    int fd = -1;
    if (!strcmp(mode, "auto") || !strcmp(mode, "broker")) {
        int result = broker_begin(control_path, &control_fd, &fd);
        if (result < 0 || (result > 0 && !strcmp(mode, "broker"))) {
            fprintf(stderr, "Broker unavailable: %s\n", strerror(errno));
            return 1;
        }
        if (result == 0)
            mode = "broker";
        else {
            mode = "device";
            path = "/dev/umts_dm0";
            init = 1;
        }
    }
    if (!strcmp(mode, "device")) {
        int pid = busy_owner(path);
        if (pid) {
            fprintf(stderr, "DM device busy/unknown (owner=%d). No modem writes performed.\n", pid);
            return 3;
        }
        if (init) {
            direct_lock = open("/data/local/tmp/sbm-diag-direct.lock",
                               O_RDWR | O_CREAT | O_NOFOLLOW | O_CLOEXEC, 0600);
            struct stat st;
            if (direct_lock < 0 || fstat(direct_lock, &st) || !S_ISREG(st.st_mode) ||
                st.st_uid != geteuid() || flock(direct_lock, LOCK_EX | LOCK_NB)) {
                fprintf(stderr, "Cannot acquire direct-session lock. No modem writes performed.\n");
                if (direct_lock >= 0)
                    close(direct_lock);
                return 3;
            }
        }
        fd = open(path, O_RDWR | O_NONBLOCK | O_CLOEXEC);
        if (fd >= 0 && (pid = busy_owner(path)) != 0) {
            fprintf(stderr, "DM ownership changed/unknown (owner=%d). No modem writes performed.\n",
                    pid);
            close(fd);
            if (direct_lock >= 0)
                close(direct_lock);
            return 3;
        }
    } else if (!strcmp(mode, "socket"))
        fd = connect_socket(path);
    else if (strcmp(mode, "broker"))
        fd = !strcmp(path, "-") ? STDIN_FILENO : open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        fprintf(stderr, "Cannot open %s: %s\n", path, strerror(errno));
        return 1;
    }
    Telemetry state;
    if (app_uid >= 0) {
        metrics.transport = !strcmp(mode, "broker") ? "broker" : "direct";
        metrics.session = !strcmp(mode, "broker") ? broker_session : "null";
        metrics.last_request = monotonic_us();
        telemetry_init(&state, metrics_publish, &metrics);
    } else
        telemetry_init(&state, telemetry_json, stdout);
    if (!strcmp(mode, "records")) {
        int result = replay(&state, fd);
        if (fd != STDIN_FILENO)
            close(fd);
        return result;
    }
    if (init && activate(fd)) {
        fprintf(stderr, "DM activation write failed: %s\n", strerror(errno));
        stop_stream(fd);
        close(fd);
        return 1;
    }
    if (init) {
        puts("{\"type\":\"session\",\"transport\":\"direct\",\"state\":{\"stream\":\"starting\","
             "\"other_client_present\":false}}");
        fflush(stdout);
    }
    Framer framer = {0};
    uint8_t buf[16384];
    int result = 0;
    int64_t start = monotonic_us();
    int64_t retry_window = start;
    unsigned stream_retries = 0;
    if (start < 0) {
        perror("clock_gettime");
        close(fd);
        return 1;
    }
    fprintf(stderr, "shannon-diagd %s connected (%s); %s\n", VERSION, mode,
            init ? "probe/LTE selection sent" : "listen only");
    while (!stopping) {
        int64_t us = monotonic_us();
        if (us < 0) {
            result = 1;
            break;
        }
        if (seconds > 0 && us - start >= (int64_t)(seconds * 1e6))
            break;
        struct pollfd fds[3] = {{fd, POLLIN, 0}, {-1, 0, 0}, {-1, 0, 0}};
        if (app_uid >= 0)
            metrics_poll(&metrics, fds + 1);
        int ready = poll(fds, app_uid >= 0 ? 3 : 1, 100);
        struct pollfd pfd = fds[0];
        if (app_uid >= 0) {
            metrics_process(&metrics, fds + 1);
            if (monotonic_us() - metrics.last_request > METRICS_WATCHDOG_US) {
                fprintf(stderr, "No app stats requests; releasing DIAG session.\n");
                break;
            }
        }
        if (ready < 0) {
            if (errno == EINTR)
                continue;
            perror("poll");
            result = 1;
            break;
        }
        if (pfd.revents & (POLLERR | POLLNVAL)) {
            fprintf(stderr, "DM input error.\n");
            result = 1;
            break;
        }
        if (ready > 0 && (pfd.revents & (POLLIN | POLLHUP))) {
            ssize_t n = read(fd, buf, sizeof(buf));
            if (n > 0)
                feed(&state, &framer, buf, (size_t)n, monotonic_us());
            else if (!n) {
                fprintf(stderr, "DM input closed (%s).\n", mode);
                if (app_uid >= 0 && !strcmp(mode, "broker") && !stopping) {
                    int64_t now = monotonic_us();
                    if (now - retry_window > 10000000) {
                        retry_window = now;
                        stream_retries = 0;
                    }
                    int replacement = stream_retries++ < 3 ? broker_resume(control_fd) : -1;
                    if (replacement >= 0) {
                        close(fd);
                        fd = replacement;
                        memset(&framer, 0, sizeof(framer));
                        telemetry_init(&state, metrics_publish, &metrics);
                        free(metrics.latest);
                        metrics.latest = NULL;
                        metrics.published_at = 0;
                        metrics.publish_after_second = now / 1000000 + 1;
                        fprintf(stderr, "Reattached broker receive stream; discarded incomplete measurements.\n");
                        continue;
                    }
                    fprintf(stderr, "Cannot resume broker stream; releasing session.\n");
                    result = 1;
                }
                break;
            } else if (errno != EINTR && errno != EAGAIN && errno != EWOULDBLOCK) {
                perror("read");
                result = 1;
                break;
            }
        }
        telemetry_advance(&state, monotonic_us());
        if (ferror(stdout)) {
            result = 1;
            break;
        }
    }
    if (stopping)
        fprintf(stderr, "DIAG stopping (app Stop or termination signal).\n");
    if (init)
        stop_stream(fd);
    fprintf(stderr,
            "read=%llu framed=%llu metrics=%llu decoded=%llu unsupported=%llu malformed=%llu "
            "frame_errors=%llu\n",
            (unsigned long long)framer.bytes_read, (unsigned long long)framer.valid,
            (unsigned long long)state.frames, (unsigned long long)state.decoded,
            (unsigned long long)state.unsupported, (unsigned long long)state.malformed,
            (unsigned long long)state.framing_errors);
    close(fd);
    broker_end(control_fd);
    if (direct_lock >= 0)
        close(direct_lock);
    if (app_uid >= 0)
        metrics_close(&metrics);
    return result;
}
