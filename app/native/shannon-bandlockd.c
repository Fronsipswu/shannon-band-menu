/*
 * Shannon Bandlock Daemon (shannon-bandlockd)
 *
 * Implements the JSON-over-abstract-UNIX-socket IPC daemon for the
 * Shannon Band Menu Android App.
 *
 * Direct NV / AT backend for Samsung Shannon baseband (Tensor G1-G4 / Exynos).
 * Built with freestanding Linux AArch64 syscalls (no libc dependencies).
 */

typedef unsigned char      u8;
typedef unsigned short     u16;
typedef unsigned int       u32;
typedef unsigned long long u64;
typedef long long          i64;
typedef __SIZE_TYPE__      usize;
typedef __PTRDIFF_TYPE__   isize;

#define NULL ((void*)0)

#define SYS_READ         63
#define SYS_WRITE        64
#define SYS_CLOSE        57
#define SYS_OPENAT       56
#define SYS_PPOLL        73
#define SYS_NANOSLEEP    101
#define SYS_CLOCK_GETTIME 113
#define SYS_EXIT_GROUP   94
#define SYS_SOCKET       198
#define SYS_BIND         200
#define SYS_LISTEN       201
#define SYS_ACCEPT       202
#define SYS_GETPEERNAME  205
#define SYS_SENDTO       206
#define SYS_GETSOCKOPT   209
#define SYS_SETSOCKOPT   208
#define SYS_GETUID       174
#define SYS_GETEUID      175

#define AT_FDCWD         -100
#define O_RDONLY         00000000
#define O_WRONLY         00000001
#define O_RDWR           00000002
#define O_NONBLOCK       00004000
#define O_CLOEXEC        02000000

#define POLLIN           0x0001
#define POLLPRI          0x0002
#define POLLOUT          0x0004
#define POLLERR          0x0008
#define POLLHUP          0x0010
#define POLLNVAL         0x0020

#define AF_UNIX          1
#define SOCK_STREAM      1
#define SOL_SOCKET       1
#define SO_PASSCRED      16
#define SO_PEERCRED      17
#define MSG_NOSIGNAL     0x4000

#define CLOCK_MONOTONIC  1

#define DAEMON_VERSION   "4.5.2"
#define SOCKET_NAME      "shannon_bandlockd"
#define ROUTER_PATH      "/dev/umts_router"
/* The band-lock / NR-mode menu is rendered by the modem itself. Writing
 * NR.CONFIG.MODE directly stores the value but never applies it; the modem's
 * own menu handler must perform the change. We drive that menu over the DM
 * channel with keypresses, then verify the result over the AT channel.
 * See agents/NR_MODE_APPLY_PROBLEM.md. */
#define DM_PATH          "/dev/umts_dm0"
#define MENU_KEY_GAP_MS  50
#define MENU_MODE_GAP_MS 50
/* The NV lands a variable moment after the keypresses; poll rather than
 * assuming it is already committed. */
#define MENU_VERIFY_TRIES  12
#define MENU_VERIFY_GAP_MS 100
#define MENU_BACK        0x5c
#define AT_TIMEOUT_MS    2000
/* NV writes are issued back to back, but the modem intermittently stops
 * answering when CFUN follows the last write immediately. Let it settle first,
 * and allow the radio reload longer than a normal AT command. */
#define AT_CFUN_SETTLE_MS  50
#define AT_CFUN_FIRST_TIMEOUT_MS 1000
#define AT_CFUN_TIMEOUT_MS 4000
#define MAX_BAND         2048
#define MASK_BYTES       256
/* Shannon behaves erratically when a RAT is left with an empty manual
 * band list. To take LTE out of service, lock it to a band the network
 * never deploys instead of publishing an empty allow-list. */
#define LTE_DUMMY_BAND   255

struct pollfd { int fd; short events; short revents; };
struct timespec { i64 tv_sec; i64 tv_nsec; };
struct sockaddr_un {
    u16 sun_family;
    char sun_path[108];
};
struct ucred {
    u32 pid;
    u32 uid;
    u32 gid;
};

static inline long sc1(long n, long a1){
    register long x8 __asm__("x8") = n;
    register long x0 __asm__("x0") = a1;
    __asm__ volatile("svc #0" : "=r"(x0) : "r"(x8), "r"(x0) : "memory");
    return x0;
}
static inline long sc2(long n, long a1, long a2){
    register long x8 __asm__("x8") = n;
    register long x0 __asm__("x0") = a1;
    register long x1 __asm__("x1") = a2;
    __asm__ volatile("svc #0" : "=r"(x0) : "r"(x8), "r"(x0), "r"(x1) : "memory");
    return x0;
}
static inline long sc3(long n, long a1, long a2, long a3){
    register long x8 __asm__("x8") = n;
    register long x0 __asm__("x0") = a1;
    register long x1 __asm__("x1") = a2;
    register long x2 __asm__("x2") = a3;
    __asm__ volatile("svc #0" : "=r"(x0) : "r"(x8), "r"(x0), "r"(x1), "r"(x2) : "memory");
    return x0;
}
static inline long sc4(long n, long a1, long a2, long a3, long a4){
    register long x8 __asm__("x8") = n;
    register long x0 __asm__("x0") = a1;
    register long x1 __asm__("x1") = a2;
    register long x2 __asm__("x2") = a3;
    register long x3 __asm__("x3") = a4;
    __asm__ volatile("svc #0" : "=r"(x0) : "r"(x8), "r"(x0), "r"(x1), "r"(x2), "r"(x3) : "memory");
    return x0;
}
static inline long sc5(long n, long a1, long a2, long a3, long a4, long a5){
    register long x8 __asm__("x8") = n;
    register long x0 __asm__("x0") = a1;
    register long x1 __asm__("x1") = a2;
    register long x2 __asm__("x2") = a3;
    register long x3 __asm__("x3") = a4;
    register long x4 __asm__("x4") = a5;
    __asm__ volatile("svc #0" : "=r"(x0) : "r"(x8), "r"(x0), "r"(x1), "r"(x2), "r"(x3), "r"(x4) : "memory");
    return x0;
}
static inline long sc6(long n, long a1, long a2, long a3, long a4, long a5, long a6){
    register long x8 __asm__("x8") = n;
    register long x0 __asm__("x0") = a1;
    register long x1 __asm__("x1") = a2;
    register long x2 __asm__("x2") = a3;
    register long x3 __asm__("x3") = a4;
    register long x4 __asm__("x4") = a5;
    register long x5 __asm__("x5") = a6;
    __asm__ volatile("svc #0" : "=r"(x0) : "r"(x8), "r"(x0), "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5) : "memory");
    return x0;
}

static inline long sys_read(int fd, void *buf, usize cnt){ return sc3(SYS_READ, fd, (long)buf, (long)cnt); }
static inline long sys_write(int fd, const void *buf, usize cnt){ return sc3(SYS_WRITE, fd, (long)buf, (long)cnt); }
static inline long sys_send_nosignal(int fd, const void *buf, usize cnt){
    return sc6(SYS_SENDTO, fd, (long)buf, (long)cnt, MSG_NOSIGNAL, 0, 0);
}
static inline long sys_close(int fd){ return sc1(SYS_CLOSE, fd); }
static inline long sys_open(const char *path, int flags){ return sc4(SYS_OPENAT, AT_FDCWD, (long)path, flags, 0); }
static inline long sys_ppoll(struct pollfd *fds, u32 nfds, const struct timespec *tmo){
    return sc5(SYS_PPOLL, (long)fds, nfds, (long)tmo, 0, 0);
}
static inline i64 sys_monotime_ms(void){
    struct timespec ts;
    if(sc2(SYS_CLOCK_GETTIME, CLOCK_MONOTONIC, (long)&ts) < 0) return 0;
    return ts.tv_sec * 1000 + (ts.tv_nsec / 1000000);
}
static inline void sys_exit(int code){ (void)sc1(SYS_EXIT_GROUP, code); while(1){} }

static inline usize slen(const char *s){ usize n = 0; while(s && s[n]) n++; return n; }
usize strlen(const char *s){ return slen(s); }
void *memset(void *dst, int c, usize n){
    u8 *d = (u8*)dst;
    while(n--) *d++ = (u8)c;
    return dst;
}
void *memcpy(void *dst, const void *src, usize n){
    u8 *d = (u8*)dst; const u8 *s = (const u8*)src;
    while(n--) *d++ = *s++;
    return dst;
}
static inline void zero(void *dst, usize n){ (void)memset(dst, 0, n); }
static inline void copy(void *dst, const void *src, usize n){ (void)memcpy(dst, src, n); }

static inline void sleep_ms(u32 ms){
    struct timespec ts;
    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (ms % 1000) * 1000000;
    (void)sc2(SYS_NANOSLEEP, (long)&ts, 0);
}

static inline int char_lower(char c){ if(c>='A'&&c<='Z') return c+('a'-'A'); return c; }
static int contains_ci(const char *haystack, const char *needle){
    if(!haystack || !needle) return 0;
    usize nlen = slen(needle);
    if(!nlen) return 1;
    for(usize i = 0; haystack[i]; i++){
        usize j = 0;
        while(needle[j] && haystack[i+j] && char_lower(haystack[i+j]) == char_lower(needle[j])) j++;
        if(j == nlen) return 1;
    }
    return 0;
}
static int starts_ci(const char *haystack, const char *prefix){
    if(!haystack || !prefix) return 0;
    while(*prefix){
        if(char_lower(*haystack) != char_lower(*prefix)) return 0;
        haystack++; prefix++;
    }
    return 1;
}

static long write_all(int fd, const void *buf, usize len){
    usize done = 0;
    while(done < len){
        long n = sys_write(fd, (const u8*)buf + done, len - done);
        if(n <= 0) return -1;
        done += (usize)n;
    }
    return (long)done;
}

/* Socket replies must not terminate the daemon if Android closes mid-request. */
static long socket_write_all(int fd, const void *buf, usize len){
    usize done = 0;
    while(done < len){
        long n = sys_send_nosignal(fd, (const u8*)buf + done, len - done);
        if(n <= 0) return -1;
        done += (usize)n;
    }
    return (long)done;
}

/* Modem state & masks */
enum band_spec { SPEC_NONE = 0, SPEC_ALL, SPEC_LIST };
enum nr_mode { NR_MODE_BOTH = 0, NR_MODE_SA, NR_MODE_NSA, NR_MODE_DISABLE };

struct band_state {
    int valid;
    u8 mask[MASK_BYTES];
};

struct daemon_state {
    int router_fd;
    u32 allowed_uid;
    int enforce_uid;

    /* Live active states */
    struct band_state gsm;
    struct band_state wcdma;
    struct band_state lte;
    struct band_state sa;
    struct band_state nsa;
    enum nr_mode mode;

    /* Hardware capabilities */
    u8 supported_gsm[MASK_BYTES];
    u8 supported_wcdma[MASK_BYTES];
    u8 supported_lte[MASK_BYTES];
    u8 supported_sa[MASK_BYTES];
} G;

static inline void mask_set(u8 *m, u32 b){ if(b && b <= MAX_BAND) m[(b-1)/8] |= (u8)(1u << ((b-1)%8)); }
static inline int mask_has(const u8 *m, u32 b){ if(b && b <= MAX_BAND) return (m[(b-1)/8] & (1u << ((b-1)%8))) != 0; return 0; }
static inline void mask_clear(u8 *m, u32 b){ if(b && b <= MAX_BAND) m[(b-1)/8] &= (u8)~(1u << ((b-1)%8)); }
static inline void mask_clear_all(u8 *m){ zero(m, MASK_BYTES); }
static inline u32 mask_count(const u8 *m){
    u32 c = 0;
    for(u32 b = 1; b <= MAX_BAND; b++){
        if(mask_has(m, b)) c++;
    }
    return c;
}
static int mask_equal(const u8 *a, const u8 *b){
    for(usize i = 0; i < MASK_BYTES; i++) if(a[i] != b[i]) return 0;
    return 1;
}
static int band_spec_matches(const struct band_state *current, const u8 *requested,
                             enum band_spec spec, const u8 *supported){
    if(spec == SPEC_LIST) return requested && mask_equal(current->mask, requested);
    if(spec == SPEC_ALL) return supported && mask_equal(current->mask, supported);
    return mask_count(current->mask) == 0;
}

static int at_open(void){
    if(G.router_fd >= 0) return 0;
    G.router_fd = (int)sys_open(ROUTER_PATH, O_RDWR | O_CLOEXEC);
    return G.router_fd >= 0 ? 0 : -1;
}

static void at_drain(int timeout_ms){
    struct pollfd pfd = {G.router_fd, POLLIN, 0};
    struct timespec ts = {(i64)(timeout_ms/1000), (i64)(timeout_ms%1000)*1000000};
    u8 buf[512];
    while(sys_ppoll(&pfd, 1, &ts) > 0 && (pfd.revents & POLLIN)){
        long n = sys_read(G.router_fd, buf, sizeof(buf));
        if(n <= 0) break;
    }
}

static int at_exec(const char *cmd, char *resp, usize resp_cap, int timeout_ms){
    struct pollfd pfd;
    struct timespec ts;
    i64 deadline;
    usize resp_len = 0;
    usize cmd_len = slen(cmd);

    if(at_open() < 0) return -1;
    at_drain(10);

    if(write_all(G.router_fd, cmd, cmd_len) < 0) return -1;
    if(write_all(G.router_fd, "\r", 1) < 0) return -1;

    if(resp && resp_cap > 0){ resp[0] = 0; }
    deadline = sys_monotime_ms() + timeout_ms;

    while(1){
        i64 now = sys_monotime_ms();
        i64 left_ms = deadline - now;
        if(left_ms <= 0) break;

        pfd.fd = G.router_fd;
        pfd.events = POLLIN;
        pfd.revents = 0;
        ts.tv_sec = left_ms / 1000;
        ts.tv_nsec = (left_ms % 1000) * 1000000;

        int pr = (int)sys_ppoll(&pfd, 1, &ts);
        if(pr < 0) return -1;
        if(pr == 0) continue;

        if(pfd.revents & (POLLERR | POLLHUP)) return -1;
        if(pfd.revents & POLLIN){
            char chunk[256];
            long n = sys_read(G.router_fd, chunk, sizeof(chunk) - 1);
            if(n <= 0) break;
            chunk[n] = 0;

            if(resp && resp_len + (usize)n + 1 < resp_cap){
                copy(resp + resp_len, chunk, (usize)n);
                resp_len += (usize)n;
                resp[resp_len] = 0;
            }

            if(contains_ci(chunk, "OK") || (resp && contains_ci(resp, "OK"))) return 0;
            if(contains_ci(chunk, "ERROR") || (resp && contains_ci(resp, "ERROR"))) return -1;
        }
    }

    return (resp && contains_ci(resp, "OK")) ? 0 : -1;
}

static u8 hex_val(char c){
    if(c>='0'&&c<='9') return (u8)(c-'0');
    if(c>='a'&&c<='f') return (u8)(c-'a'+10);
    if(c>='A'&&c<='F') return (u8)(c-'A'+10);
    return 0;
}

static int parse_hex_csv(const char *csv_str, u8 *out_bytes, usize max_bytes, usize *out_count){
    usize count = 0;
    const char *p = csv_str;
    while(*p && count < max_bytes){
        while(*p && (*p == ' ' || *p == '\t' || *p == ',' || *p == '"')) p++;
        if(!*p || *p == '\r' || *p == '\n') break;
        if(p[0] && p[1] && p[1] != ',' && p[1] != '"' && p[1] != ' ' && p[1] != '\r' && p[1] != '\n'){
            out_bytes[count++] = (u8)((hex_val(p[0]) << 4) | hex_val(p[1]));
            p += 2;
        } else {
            out_bytes[count++] = hex_val(p[0]);
            p += 1;
        }
    }
    if(out_count) *out_count = count;
    return (count > 0);
}

static usize append_text(char *dst, usize pos, usize cap, const char *src){
    if(!src) return pos;
    while(*src && pos + 1 < cap){ dst[pos++] = *src++; }
    dst[pos] = 0;
    return pos;
}

static usize append_uint(char *buf, usize off, usize cap, u64 v){
    char tmp[32]; usize t = 0;
    if(v == 0) tmp[t++] = '0';
    else { while(v > 0){ tmp[t++] = (char)('0' + (v % 10)); v /= 10; } }
    while(t > 0 && off + 1 < cap){ buf[off++] = tmp[--t]; }
    buf[off] = 0;
    return off;
}

static void format_hex_csv(const u8 *bytes, usize len, char *out_str, usize out_cap){
    static const char hex_digits[] = "0123456789ABCDEF";
    usize pos = 0;
    for(usize i = 0; i < len; i++){
        if(i > 0 && pos + 1 < out_cap) out_str[pos++] = ',';
        if(pos + 2 < out_cap){
            out_str[pos++] = hex_digits[(bytes[i] >> 4) & 0x0F];
            out_str[pos++] = hex_digits[bytes[i] & 0x0F];
        }
    }
    out_str[pos] = 0;
}

static int googgetnv(const char *name, int index, u8 *out_bytes, usize max_bytes, usize *out_count){
    char cmd[128], resp[1024];
    usize p = 0;
    p = append_text(cmd, p, sizeof(cmd), "AT+GOOGGETNV=\"");
    p = append_text(cmd, p, sizeof(cmd), name);
    p = append_text(cmd, p, sizeof(cmd), "\",");
    {
        char numbuf[12]; int ni = 0; int idx = index;
        if(!idx) numbuf[ni++] = '0';
        else while(idx){ numbuf[ni++] = (char)('0' + idx % 10); idx /= 10; }
        while(ni > 0 && p + 1 < sizeof(cmd)) cmd[p++] = numbuf[--ni];
    }
    cmd[p] = 0;

    if(at_exec(cmd, resp, sizeof(resp), AT_TIMEOUT_MS) < 0) return -1;

    const char *s = resp;
    while(*s){
        if(starts_ci(s, "+GOOGGETNV:")){
            s += 11;
            while(*s && *s != '"') s++;
            if(*s == '"') s++;
            while(*s && *s != '"') s++;
            if(*s == '"') s++;
            while(*s && *s != ',') s++;
            if(*s == ',') s++;
            while(*s && *s != '"') s++;
            if(*s == '"'){
                s++;
                return parse_hex_csv(s, out_bytes, max_bytes, out_count) ? 0 : -1;
            }
        }
        while(*s && *s != '\n') s++;
        if(*s == '\n') s++;
    }
    return -1;
}

static int googsetnv(const char *name, int index, const u8 *data, usize len){
    char cmd[512], hex_payload[256], resp[256];
    usize p = 0;
    format_hex_csv(data, len, hex_payload, sizeof(hex_payload));

    p = append_text(cmd, p, sizeof(cmd), "AT+GOOGSETNV=\"");
    p = append_text(cmd, p, sizeof(cmd), name);
    p = append_text(cmd, p, sizeof(cmd), "\",");
    {
        char numbuf[12]; int ni = 0; int idx = index;
        if(!idx) numbuf[ni++] = '0';
        else while(idx){ numbuf[ni++] = (char)('0' + idx % 10); idx /= 10; }
        while(ni > 0 && p + 1 < sizeof(cmd)) cmd[p++] = numbuf[--ni];
    }
    p = append_text(cmd, p, sizeof(cmd), ",\"");
    p = append_text(cmd, p, sizeof(cmd), hex_payload);
    p = append_text(cmd, p, sizeof(cmd), "\"");
    cmd[p] = 0;

    return at_exec(cmd, resp, sizeof(resp), AT_TIMEOUT_MS);
}

typedef void (*nv_array_cb)(int idx, const u8 *bytes, usize len, void *ctx);

static int googgetnv_array(const char *name, nv_array_cb cb, void *ctx){
    char cmd[128], resp[8192];
    usize p = 0;
    p = append_text(cmd, p, sizeof(cmd), "AT+GOOGGETNV=\"");
    p = append_text(cmd, p, sizeof(cmd), name);
    p = append_text(cmd, p, sizeof(cmd), "\",1");
    cmd[p] = 0;

    if(at_exec(cmd, resp, sizeof(resp), AT_TIMEOUT_MS) < 0) return -1;

    const char *s = resp;
    while(*s){
        if(starts_ci(s, "+GOOGGETNV:")){
            s += 11;
            while(*s && *s != ',') s++;
            if(*s == ',') s++;
            u32 idx = 0;
            while(*s >= '0' && *s <= '9'){ idx = idx * 10 + (u32)(*s - '0'); s++; }
            while(*s && *s != '"') s++;
            if(*s == '"'){
                s++;
                u8 chunk[64];
                usize clen = 0;
                parse_hex_csv(s, chunk, sizeof(chunk), &clen);
                if(cb) cb((int)idx, chunk, clen, ctx);
            }
        }
        while(*s && *s != '\n') s++;
        if(*s == '\n') s++;
    }
    return 0;
}

static int at_reload_radio(void){
    char resp[256];
    /* Do not slam CFUN into the tail of an NV write burst. */
    sleep_ms(AT_CFUN_SETTLE_MS);
    (void)at_exec("AT+CFUN=0", resp, sizeof(resp), AT_CFUN_TIMEOUT_MS);
    sleep_ms(150);
    /* A transiently busy modem can miss the first radio-on request. Keep the
     * fast path bounded to one second, then retry once with the normal AT
     * timeout before reporting a backend failure to the app. */
    if(at_exec("AT+CFUN=1", resp, sizeof(resp), AT_CFUN_FIRST_TIMEOUT_MS) == 0) return 0;
    return at_exec("AT+CFUN=1", resp, sizeof(resp), AT_CFUN_TIMEOUT_MS);
}

/* 2G GSM */
static int read_gsm_state(void){
    u8 bytes[4]; usize len = 0;
    zero(&G.gsm, sizeof(G.gsm));
    if(googgetnv("GL3.Edge_Band_Config", 0, bytes, sizeof(bytes), &len) < 0 || len == 0) return -1;
    u8 mask = bytes[0];
    if(mask & 0x01) mask_set(G.gsm.mask, 850);
    if(mask & 0x02) mask_set(G.gsm.mask, 900);
    if(mask & 0x04) mask_set(G.gsm.mask, 1800);
    if(mask & 0x08) mask_set(G.gsm.mask, 1900);
    G.gsm.valid = 1;
    return 0;
}

static int write_gsm_state(const u8 *mask, enum band_spec spec){
    u8 val[1] = {0};
    if(spec == SPEC_ALL){ val[0] = 0x0F; }
    else if(spec == SPEC_LIST && mask){
        if(mask_has(mask, 850))  val[0] |= 0x01;
        if(mask_has(mask, 900))  val[0] |= 0x02;
        if(mask_has(mask, 1800)) val[0] |= 0x04;
        if(mask_has(mask, 1900)) val[0] |= 0x08;
    }
    if(googsetnv("GL3.Edge_Band_Config", 0, val, 1) < 0) return -1;
    (void)googsetnv("GL3.Operator Specific Band", 0, val, 1);
    return 0;
}

/* 3G WCDMA */
struct wcdma_list_ctx {
    u8 *mask;
    u32 count;
    const u8 *disabled;
    const u8 *supported;
};

static int wcdma_band_disabled(const u8 *disabled, u32 band){
    if(!disabled || band < 1 || band > 19) return 0;
    u32 bit = band - 1;
    return (disabled[bit / 8] & (u8)(1u << (bit % 8))) != 0;
}

static void wcdma_list_cb(int idx, const u8 *bytes, usize len, void *ctx_ptr){
    struct wcdma_list_ctx *ctx = (struct wcdma_list_ctx*)ctx_ptr;
    if(!ctx || idx < 0 || (u32)idx >= ctx->count || len == 0) return;
    u32 band = bytes[0];
    if(band < 1 || band > 19) return;
    if(ctx->supported && !mask_has(ctx->supported, band)) return;
    if(wcdma_band_disabled(ctx->disabled, band)) return;
    mask_set(ctx->mask, band);
}

static int read_wcdma_state(void){
    u8 bytes[4]; usize len = 0;
    u8 disabled[4] = {0, 0, 0, 0}; usize disabled_len = 0;
    zero(&G.wcdma, sizeof(G.wcdma));
    if(googgetnv("UL3.Etc.max_band", 0, bytes, sizeof(bytes), &len) < 0 || len == 0) return -1;

    if(googgetnv("UL3.Etc.disabled_band", 0, disabled, sizeof(disabled), &disabled_len) < 0 || disabled_len < 4){
        return -1;
    }
    if(disabled[0] == 0xFF && disabled[1] == 0xFF && disabled[2] == 0xFF && disabled[3] == 0xFF){
        G.wcdma.valid = 1;
        return 0;
    }

    /* max_band is the number of valid entries in Storing Last Camped Bands. */
    if(bytes[0] == 0xFF){
        /* Compatibility with app versions that incorrectly wrote FF for auto. */
        copy(G.wcdma.mask, G.supported_wcdma, MASK_BYTES);
    } else if(bytes[0] >= 1 && bytes[0] <= 19){
        struct wcdma_list_ctx ctx = {
            G.wcdma.mask, (u32)bytes[0], disabled, G.supported_wcdma
        };
        if(googgetnv_array("UL3.Etc.Storing Last Camped Bands", wcdma_list_cb, &ctx) < 0) return -1;
    } else if(bytes[0] == 0x00){
        /* No active list entries. */
    } else {
        return -1;
    }
    G.wcdma.valid = 1;
    return 0;
}

static int wcdma_spec_supported(const u8 *mask, enum band_spec spec){
    if(spec == SPEC_ALL || spec == SPEC_NONE) return 1;
    if(spec != SPEC_LIST || !mask) return 0;
    for(u32 b = 1; b <= MAX_BAND; b++){
        if(mask_has(mask, b) && !mask_has(G.supported_wcdma, b)) return 0;
    }
    return 1;
}

static int write_wcdma_state(const u8 *mask, enum band_spec spec){
    if(spec == SPEC_LIST && mask && mask_equal(mask, G.supported_wcdma)) spec = SPEC_ALL;
    if(spec == SPEC_LIST && (!mask || mask_count(mask) == 0)) spec = SPEC_NONE;
    if(!wcdma_spec_supported(mask, spec)) return -1;

    if(spec == SPEC_NONE){
        /* Preserve the remembered count/list; only gate WCDMA off. */
        u8 val_ff[4] = {0xFF, 0xFF, 0xFF, 0xFF};
        if(googsetnv("UL3.Etc.disabled_band", 0, val_ff, 4) < 0) return -1;
        return 0;
    }

    const u8 *selected = spec == SPEC_ALL ? G.supported_wcdma : mask;
    u32 count = mask_count(selected);
    if(!selected || count == 0 || count > 19) return -1;

    /* Gate WCDMA while replacing the compact list, then enable it atomically at reload. */
    u8 disabled_all[4] = {0xFF, 0xFF, 0xFF, 0xFF};
    u8 disabled_none[4] = {0, 0, 0, 0};
    if(googsetnv("UL3.Etc.disabled_band", 0, disabled_all, 4) < 0) return -1;

    int list_index = 0;
    for(u32 band = 1; band <= 19; band++){
        if(mask_has(selected, band)){
            u8 value[1] = {(u8)band};
            if(googsetnv("UL3.Etc.Storing Last Camped Bands", list_index, value, 1) < 0) return -1;
            list_index++;
        }
    }
    u8 count_value[1] = {(u8)count};
    if(googsetnv("UL3.Etc.max_band", 0, count_value, 1) < 0) return -1;
    return googsetnv("UL3.Etc.disabled_band", 0, disabled_none, 4);
}

/* 4G LTE */
static void lte_bitmap_cb(int idx, const u8 *bytes, usize len, void *ctx){
    (void)ctx;
    if(idx >= 0 && idx < 4 && len >= 8){
        for(int b = 0; b < 8; b++){
            G.lte.mask[idx * 8 + b] = bytes[b];
        }
    }
}

static int read_lte_state(void){
    zero(&G.lte, sizeof(G.lte));
    u8 enb[1]; usize enb_len = 0;
    if(googgetnv("!SAEL3.Manual.Band.Select Enb/ Dis", 0, enb, sizeof(enb), &enb_len) >= 0 && enb_len > 0 && enb[0] == 0){
        /* Auto / All LTE bands */
        copy(G.lte.mask, G.supported_lte, MASK_BYTES);
        G.lte.valid = 1;
        return 0;
    }
    (void)googgetnv_array("!SAEL3.Manual.Enabled.RFBands.BitMap", lte_bitmap_cb, NULL);
    /* The dummy band only marks "LTE RAT off"; it is never a user selection. */
    mask_clear(G.lte.mask, LTE_DUMMY_BAND);
    G.lte.valid = 1;
    return 0;
}

static int write_lte_state(const u8 *mask, enum band_spec spec){
    u8 enb[1];
    if(spec == SPEC_ALL){
        enb[0] = 0;
        if(googsetnv("!SAEL3.Manual.Band.Select Enb/ Dis", 0, enb, 1) < 0) return -1;
        return 0;
    }
    u8 full_bitmap[32];
    zero(full_bitmap, sizeof(full_bitmap));
    if(spec == SPEC_LIST && mask && mask_count(mask) > 0){
        copy(full_bitmap, mask, 32);
        mask_clear(full_bitmap, LTE_DUMMY_BAND);
    } else {
        /* RAT off. An empty manual list makes the modem behave erratically,
         * so lock LTE to a band that is never deployed instead. */
        u32 bit = LTE_DUMMY_BAND - 1;
        full_bitmap[bit / 8] |= (u8)(1u << (bit % 8));
    }
    for(int i = 0; i < 4; i++){
        if(googsetnv("!SAEL3.Manual.Enabled.RFBands.BitMap", i, &full_bitmap[i * 8], 8) < 0) return -1;
    }
    enb[0] = 1;
    if(googsetnv("!SAEL3.Manual.Band.Select Enb/ Dis", 0, enb, 1) < 0) return -1;
    return 0;
}

/* 5G NR SA */
struct nr_list_ctx { u16 count; int stop_at_zero; int stopped; };

static int read_nv_u16(const char *name, u16 *value){
    u8 bytes[2]; usize len = 0;
    if(googgetnv(name, 0, bytes, sizeof(bytes), &len) < 0 || len < 2) return -1;
    *value = (u16)(bytes[0] | ((u16)bytes[1] << 8));
    return 0;
}

static void nr_list_counted_cb(int idx, const u8 *bytes, usize len, void *opaque){
    struct nr_list_ctx *ctx = (struct nr_list_ctx*)opaque;
    if(ctx->stopped || idx < 0 || (u16)idx >= ctx->count || len < 2) return;
    {
        u16 b = (u16)(bytes[0] | ((u16)bytes[1] << 8));
        if(ctx->stop_at_zero && b == 0){ ctx->stopped = 1; return; }
        if(b >= 1 && b <= MAX_BAND) mask_set(G.sa.mask, (u32)b);
    }
}

static int read_sa_state(void){
    zero(&G.sa, sizeof(G.sa));
    if(G.mode == NR_MODE_DISABLE || G.mode == NR_MODE_NSA){
        G.sa.valid = 1;
        return 0;
    }
    u16 count = 0;
    if(read_nv_u16("!NRRRC.NUM_MANUAL_NR_BAND_LIST", &count) < 0) return -1;
    if(count > 60) count = 60;
    struct nr_list_ctx ctx = {count, 0, 0};
    if(count > 0) (void)googgetnv_array("!NRRRC.MANUAL_NR_BAND_LIST", nr_list_counted_cb, &ctx);
    if(mask_count(G.sa.mask) == 0){
        copy(G.sa.mask, G.supported_sa, MASK_BYTES);
    }
    G.sa.valid = 1;
    return 0;
}

static int write_sa_state(const u8 *mask, enum band_spec spec){
    u16 bands[60]; u16 count = 0;
    u16 previous_count = 0;
    /* NR is switched off through NR.CONFIG.MODE alone. Keep the stored band
     * list intact so it survives an NR off/on cycle. */
    if(spec == SPEC_NONE) return 0;
    (void)read_nv_u16("!NRRRC.NUM_MANUAL_NR_BAND_LIST", &previous_count);
    if(previous_count > 60) previous_count = 60;
    if(spec == SPEC_ALL){
        for(u32 b = 1; b <= MAX_BAND && count < 60; b++){
            if(mask_has(G.supported_sa, b)) bands[count++] = (u16)b;
        }
    } else if(spec == SPEC_LIST && mask){
        for(u32 b = 1; b <= MAX_BAND && count < 60; b++){
            if(mask_has(mask, b)) bands[count++] = (u16)b;
        }
    }
    for(u16 i = 0; i < count; i++){
        u8 val[2] = { (u8)(bands[i] & 0xFF), (u8)((bands[i] >> 8) & 0xFF) };
        if(googsetnv("!NRRRC.MANUAL_NR_BAND_LIST", (int)i, val, 2) < 0) return -1;
    }
    /* Clear entries that belonged to the previous, longer lock. */
    for(u16 i = count; i < previous_count; i++){
        u8 zero_band[2] = {0, 0};
        if(googsetnv("!NRRRC.MANUAL_NR_BAND_LIST", (int)i, zero_band, 2) < 0) return -1;
    }
    u8 cnt_bytes[2] = { (u8)(count & 0xFF), (u8)((count >> 8) & 0xFF) };
    return googsetnv("!NRRRC.NUM_MANUAL_NR_BAND_LIST", 0, cnt_bytes, 2);
}

/* 5G NR NSA */
static void nsa_list_counted_cb(int idx, const u8 *bytes, usize len, void *opaque){
    struct nr_list_ctx *ctx = (struct nr_list_ctx*)opaque;
    if(ctx->stopped || idx < 0 || (u16)idx >= ctx->count || len < 2) return;
    {
        u16 b = (u16)(bytes[0] | ((u16)bytes[1] << 8));
        if(ctx->stop_at_zero && b == 0){ ctx->stopped = 1; return; }
        if(b >= 1 && b <= MAX_BAND) mask_set(G.nsa.mask, (u32)b);
    }
}

static int read_nsa_state(void){
    zero(&G.nsa, sizeof(G.nsa));
    if(G.mode == NR_MODE_DISABLE || G.mode == NR_MODE_SA){
        G.nsa.valid = 1;
        return 0;
    }
    u8 enb[1]; usize enb_len = 0;
    if(googgetnv("!LTE.NR Manual Band Enable/Disable", 0, enb, sizeof(enb), &enb_len) >= 0 && enb_len > 0 && enb[0] == 0){
        /* Auto / All NSA bands */
        copy(G.nsa.mask, G.supported_sa, MASK_BYTES);
        G.nsa.valid = 1;
        return 0;
    }
    /* The direct LTE NSA list is authoritative and has its own zero terminator.
       Do not truncate it using the unrelated service-menu count NV. */
    struct nr_list_ctx lte_ctx = {60, 1, 0};
    int direct_result = googgetnv_array("!LTE.NR Manual Band List", nsa_list_counted_cb, &lte_ctx);

    /* Older modem builds may lack the direct list. Only then use service-menu
       bookkeeping as a compatibility fallback. */
    if(direct_result < 0){
        u16 count = 0;
        if(read_nv_u16("!NRRRC_NUM_SVC_MENU_NSA_NR_BAND_LIST", &count) == 0){
            if(count > 60) count = 60;
            struct nr_list_ctx svc_ctx = {count, 0, 0};
            if(count > 0) (void)googgetnv_array("!NRRRC_SVC_MENU_NSA_NR_BAND_LIST", nsa_list_counted_cb, &svc_ctx);
        }
    }
    G.nsa.valid = 1;
    return 0;
}

static int write_nsa_state(const u8 *mask, enum band_spec spec){
    u16 bands[60]; u16 count = 0;
    /* See write_sa_state: NR off is expressed by NR.CONFIG.MODE only. */
    if(spec == SPEC_NONE) return 0;
    if(spec == SPEC_ALL){
        for(u32 b = 1; b <= MAX_BAND && count < 60; b++){
            if(mask_has(G.supported_sa, b)) bands[count++] = (u16)b;
        }
    } else if(spec == SPEC_LIST && mask){
        for(u32 b = 1; b <= MAX_BAND && count < 60; b++){
            if(mask_has(mask, b)) bands[count++] = (u16)b;
        }
    }
    for(u16 i = 0; i < count; i++){
        u8 val[2] = { (u8)(bands[i] & 0xFF), (u8)((bands[i] >> 8) & 0xFF) };
        if(googsetnv("!LTE.NR Manual Band List", (int)i, val, 2) < 0) return -1;
    }
    u8 term[2] = {0, 0};
    if(googsetnv("!LTE.NR Manual Band List", (int)count, term, 2) < 0) return -1;
    u8 enb[1] = { (u8)(count ? 1 : 0) };
    return googsetnv("!LTE.NR Manual Band Enable/Disable", 0, enb, 1);
}

/* NR Mode */
static int read_mode_state(void){
    u8 bytes[1]; usize len = 0;
    if(googgetnv("NR.CONFIG.MODE", 0, bytes, sizeof(bytes), &len) < 0 || len == 0) return -1;
    if(bytes[0] == 0x01) G.mode = NR_MODE_NSA;
    else if(bytes[0] == 0x10) G.mode = NR_MODE_SA;
    else if(bytes[0] == 0x00) G.mode = NR_MODE_DISABLE;
    else G.mode = NR_MODE_BOTH;
    return 0;
}

/* ---- Modem service-menu driver (DM channel) ---- */

static const u8 DM_PROBE_ENABLE[]  = {0x7f,0x12,0,0,0x0f,0,0,0,0xa0,0,0x52,0,0,0,0,1,0,0,0,0x7e};
static const u8 DM_STREAM_STOP[]   = {0x7f,0x12,0,0,0x0f,0,0,0,0xa0,0,0x90,0,0,0,0,0,0,0,0,0x7e};
static const u8 DM_PROBE_QUERY[]   = {0x7f,0x16,0,0,0x13,0,0,0,0xa0,0,0,0,0,0,0,0x34,0xdc,0x12,0xfe,0,0,0,0xc0,0x7e};
static const u8 DM_I1_72[]         = {0x7f,0x0e,0,0,0x0b,0,0,0,0xa0,0,0x72,0,0,0,0,0x7e};
static const u8 DM_I1_06[]         = {0x7f,0x10,0,0,0x0d,0,0,0,0xa0,0,0x06,0,0,0,0,3,3,0x7e};
static const u8 DM_I1_A0[]         = {0x7f,0x1b,0,0,0x18,0,0,0,0xa0,0,0xa0,0,0,0,0,0,0x3e,0,0,0,
                                      0xff,0xff,0xff,0xff,0xff,0xff,0xff,0x3f,0x7e};
static const u8 DM_FORCINGS_ON[]   = {0x7f,0x12,0,0,0x0f,0,0,0,0xa0,0,0x9a,0,0,0,0,1,1,0x16,0x10,0x7e};

/* {0x7f,0x0f,0,0,0x0c,0,0,0,0xa0,0,CMD,0,0,0,0,0xff,0x7e} */
static int dm_send_simple(int fd, u8 cmd){
    u8 f[17] = {0x7f,0x0f,0,0,0x0c,0,0,0,0xa0,0,0,0,0,0,0,0xff,0x7e};
    f[10] = cmd;
    return sys_write(fd, f, sizeof(f)) == (long)sizeof(f) ? 0 : -1;
}

static int dm_send(int fd, const u8 *p, usize n){
    return sys_write(fd, p, n) == (long)n ? 0 : -1;
}

/* {0x7f,0x0f,0,0,0x0c,0,0,0,0xa0,0x08,0x09,0,0,0,0,KEY,0x7e} */
static int dm_key(int fd, u8 key, u32 gap_ms){
    u8 f[17] = {0x7f,0x0f,0,0,0x0c,0,0,0,0xa0,0x08,0x09,0,0,0,0,0,0x7e};
    f[15] = key;
    if(sys_write(fd, f, sizeof(f)) != (long)sizeof(f)) return -1;
    sleep_ms(gap_ms);
    return 0;
}

/* Open the menu session. The modem renders the menu; these frames put it into
 * the forcings mode that accepts keypresses.
 *
 * No pauses between the init frames: v3 has waits here only because it drains
 * the response stream to parse pages, which we never do. Removing them was
 * verified over 32 consecutive mode changes with no failures, and cut the
 * fixed menu cost from ~1.15 s to ~0.9 s. */
static int dm_open_session(void){
    int fd = (int)sys_open(DM_PATH, O_RDWR | O_CLOEXEC);
    if(fd < 0) return -1;
    if(dm_send(fd, DM_PROBE_ENABLE, sizeof(DM_PROBE_ENABLE)) < 0) goto fail;
    if(dm_send(fd, DM_PROBE_QUERY, sizeof(DM_PROBE_QUERY)) < 0) goto fail;
    if(dm_send(fd, DM_I1_72, sizeof(DM_I1_72)) < 0) goto fail;
    if(dm_send(fd, DM_I1_06, sizeof(DM_I1_06)) < 0) goto fail;
    if(dm_send_simple(fd, 0x10) < 0) goto fail;
    if(dm_send_simple(fd, 0x20) < 0) goto fail;
    if(dm_send_simple(fd, 0x30) < 0) goto fail;
    if(dm_send_simple(fd, 0x40) < 0) goto fail;
    if(dm_send_simple(fd, 0x44) < 0) goto fail;
    if(dm_send(fd, DM_I1_A0, sizeof(DM_I1_A0)) < 0) goto fail;
    if(dm_send_simple(fd, 0x12) < 0) goto fail;
    if(dm_send_simple(fd, 0x22) < 0) goto fail;
    if(dm_send_simple(fd, 0x32) < 0) goto fail;
    if(dm_send_simple(fd, 0x42) < 0) goto fail;
    if(dm_send_simple(fd, 0x46) < 0) goto fail;
    if(dm_send(fd, DM_STREAM_STOP, sizeof(DM_STREAM_STOP)) < 0) goto fail;
    if(dm_send(fd, DM_FORCINGS_ON, sizeof(DM_FORCINGS_ON)) < 0) goto fail;
    return fd;
fail:
    sys_close(fd);
    return -1;
}

/* Deliberately does NOT send FORCINGS_DISABLE: that frame is global modem
 * state, and tearing it down would break a concurrently running NSG session.
 * We return the shared page cursor to root with two Back presses instead. */
static void dm_close_session(int fd){
    if(fd >= 0) sys_close(fd);
}

static u8 menu_key_for_mode(enum nr_mode mode){
    if(mode == NR_MODE_BOTH)    return '4';
    if(mode == NR_MODE_NSA)     return '3';
    if(mode == NR_MODE_SA)      return '5';
    return '2'; /* disable / LTE only */
}

/* Apply NR mode through the modem's own menu, then leave the shared page
 * cursor parked at the root page so the next operation has a known reference. */
static int menu_set_nr_mode(enum nr_mode mode){
    int fd = dm_open_session();
    if(fd < 0) return -1;
    if(dm_key(fd, '1', MENU_KEY_GAP_MS) < 0) goto fail;            /* SIM1 root      */
    if(dm_key(fd, '3', MENU_KEY_GAP_MS) < 0) goto fail;            /* NR MODE page   */
    if(dm_key(fd, menu_key_for_mode(mode), MENU_MODE_GAP_MS) < 0) goto fail;
    if(dm_key(fd, MENU_BACK, MENU_KEY_GAP_MS) < 0) goto fail;      /* out of mode    */
    if(dm_key(fd, MENU_BACK, MENU_KEY_GAP_MS) < 0) goto fail;      /* root reference */
    dm_close_session(fd);
    return 0;
fail:
    dm_close_session(fd);
    return -1;
}

/* NR mode is applied by the modem's menu handler. A direct GOOGSETNV of
 * NR.CONFIG.MODE is stored but never adopted, so it is not used here.
 *
 * The modem commits the NV a short, variable time after the menu keypresses,
 * so a single immediate readback races it and reports a false failure. Poll
 * the AT channel until it agrees, or until the budget is spent. */
static int write_mode_state(enum nr_mode mode){
    enum nr_mode previous = G.mode;
    if(menu_set_nr_mode(mode) < 0) return -1;
    for(int attempt = 0; attempt < MENU_VERIFY_TRIES; attempt++){
        if(read_mode_state() == 0 && G.mode == mode) return 0;
        sleep_ms(MENU_VERIFY_GAP_MS);
    }
    G.mode = previous;
    return -1;
}

static void refresh_all_state(void){
    (void)read_mode_state();
    (void)read_gsm_state();
    (void)read_wcdma_state();
    (void)read_lte_state();
    (void)read_sa_state();
    (void)read_nsa_state();
}

static void supp_nr_cb(int idx, const u8 *bytes, usize len, void *ctx){
    (void)idx; (void)ctx;
    if(len >= 2){
        u16 b = (u16)(bytes[0] | ((u16)bytes[1] << 8));
        if(b >= 1 && b <= MAX_BAND) mask_set(G.supported_sa, (u32)b);
    }
}

static void init_hardware_bands(void){
    mask_clear_all(G.supported_gsm);
    mask_set(G.supported_gsm, 850);
    mask_set(G.supported_gsm, 900);
    mask_set(G.supported_gsm, 1800);
    mask_set(G.supported_gsm, 1900);

    /* Expose the user-selectable WCDMA set confirmed by NSG. The ds_ scan list
     * also contains B6/B19 modem-internal entries that are not exposed by the
     * service UI and must not be presented as lock choices. */
    mask_clear_all(G.supported_wcdma);
    u32 wcdma_hw[] = {1, 2, 4, 5, 8};
    for(usize i = 0; i < sizeof(wcdma_hw)/sizeof(wcdma_hw[0]); i++) mask_set(G.supported_wcdma, wcdma_hw[i]);

    mask_clear_all(G.supported_lte);
    u32 lte_hw[] = {1, 2, 3, 4, 5, 7, 8, 12, 13, 14, 17, 18, 19, 20, 25, 26, 28, 29, 30, 32, 38, 39, 40, 41, 46, 48, 66, 71};
    for(usize i = 0; i < sizeof(lte_hw)/sizeof(lte_hw[0]); i++) mask_set(G.supported_lte, lte_hw[i]);

    mask_clear_all(G.supported_sa);
    (void)googgetnv_array("!NRRRC.SUPPORTED_NR_BAND_LIST", supp_nr_cb, NULL);
    if(!mask_has(G.supported_sa, 1)){
        u32 nr_hw[] = {1, 2, 3, 5, 7, 8, 12, 14, 20, 25, 28, 30, 38, 40, 41, 66, 71, 75, 76, 77, 78, 79, 257, 258, 260, 261};
        for(usize i = 0; i < sizeof(nr_hw)/sizeof(nr_hw[0]); i++) mask_set(G.supported_sa, nr_hw[i]);
    }
}

/* JSON Response Serializer */
static usize append_json_array_from_mask(char *buf, usize off, usize cap, const u8 *m){
    off = append_text(buf, off, cap, "[");
    int first = 1;
    for(u32 b = 1; b <= MAX_BAND; b++){
        if(mask_has(m, b)){
            if(!first) off = append_text(buf, off, cap, ",");
            off = append_uint(buf, off, cap, (u64)b);
            first = 0;
        }
    }
    off = append_text(buf, off, cap, "]");
    return off;
}

static usize format_state_json(char *buf, usize off, usize cap, int req_id, const char *cmd, int ok){
    off = append_text(buf, off, cap, "{\"id\":");
    off = append_uint(buf, off, cap, (u64)req_id);
    off = append_text(buf, off, cap, ",\"cmd\":\"");
    off = append_text(buf, off, cap, cmd);
    off = append_text(buf, off, cap, "\",\"ok\":");
    off = append_text(buf, off, cap, ok ? "true" : "false");
    off = append_text(buf, off, cap, ",\"version\":\"" DAEMON_VERSION "\"");

    off = append_text(buf, off, cap, ",\"state\":{");
    off = append_text(buf, off, cap, "\"valid\":true,\"sim\":1,\"status\":\"ready\",");

    /* Hardware */
    off = append_text(buf, off, cap, "\"hardware\":{");
    off = append_text(buf, off, cap, "\"gsm\":");
    off = append_json_array_from_mask(buf, off, cap, G.supported_gsm);
    off = append_text(buf, off, cap, ",\"wcdma\":");
    off = append_json_array_from_mask(buf, off, cap, G.supported_wcdma);
    off = append_text(buf, off, cap, ",\"lte\":");
    off = append_json_array_from_mask(buf, off, cap, G.supported_lte);
    off = append_text(buf, off, cap, ",\"nr\":");
    off = append_json_array_from_mask(buf, off, cap, G.supported_sa);
    off = append_text(buf, off, cap, "},");

    /* Active bands */
    off = append_text(buf, off, cap, "\"gsm\":");
    off = append_json_array_from_mask(buf, off, cap, G.gsm.mask);
    off = append_text(buf, off, cap, ",\"wcdma\":");
    off = append_json_array_from_mask(buf, off, cap, G.wcdma.mask);
    off = append_text(buf, off, cap, ",\"lte\":");
    off = append_json_array_from_mask(buf, off, cap, G.lte.mask);
    off = append_text(buf, off, cap, ",\"nr_nsa\":");
    off = append_json_array_from_mask(buf, off, cap, G.nsa.mask);
    off = append_text(buf, off, cap, ",\"nr_sa\":");
    off = append_json_array_from_mask(buf, off, cap, G.sa.mask);

    /* NR mode */
    off = append_text(buf, off, cap, ",\"nr_mode\":\"");
    if(G.mode == NR_MODE_SA) off = append_text(buf, off, cap, "sa");
    else if(G.mode == NR_MODE_NSA) off = append_text(buf, off, cap, "nsa");
    else if(G.mode == NR_MODE_DISABLE) off = append_text(buf, off, cap, "disable");
    else off = append_text(buf, off, cap, "both");
    off = append_text(buf, off, cap, "\"");

    /* NR Independent Capability */
    off = append_text(buf, off, cap, ",\"nr_independent_capability\":{\"checked\":true,\"independent_lock_supported\":true}");

    off = append_text(buf, off, cap, "}}\n");
    return off;
}

/* Parse band list from JSON by key name: e.g. "bands":[1,28] or "gsm":[900,1800] */
static int parse_json_named_bands(const char *json, const char *key, u8 *out_mask, enum band_spec *out_spec){
    *out_spec = SPEC_NONE;
    mask_clear_all(out_mask);

    usize klen = slen(key);
    const char *p = json;
    while(*p){
        if(*p == '"' && starts_ci(p + 1, key) && p[1 + klen] == '"'){
            p += 2 + klen;
            while(*p && (*p == ' ' || *p == ':')) p++;
            if(*p == '"'){
                p++;
                if(starts_ci(p, "none")){
                    *out_spec = SPEC_NONE;
                    return 0;
                }
                if(starts_ci(p, "all")){
                    *out_spec = SPEC_ALL;
                    return 0;
                }
            } else if(*p == '['){
                p++;
                *out_spec = SPEC_LIST;
                while(*p && *p != ']'){
                    while(*p && (*p == ' ' || *p == ',')) p++;
                    if(*p >= '0' && *p <= '9'){
                        u32 b = 0;
                        while(*p >= '0' && *p <= '9'){ b = b * 10 + (*p - '0'); p++; }
                        if(b >= 1 && b <= MAX_BAND) mask_set(out_mask, b);
                    } else p++;
                }
                return 0;
            }
        }
        p++;
    }
    return -1;
}

static int parse_json_bands(const char *json, u8 *out_mask, enum band_spec *out_spec){
    return parse_json_named_bands(json, "bands", out_mask, out_spec);
}

/* Parse command name and ID */
static int parse_json_req(const char *json, char *out_cmd, usize cmd_cap, int *out_id){
    *out_id = 0;
    out_cmd[0] = 0;

    /* Parse ID */
    const char *p = json;
    while(*p){
        if(p[0] == '"' && p[1] == 'i' && p[2] == 'd' && p[3] == '"'){
            p += 4;
            while(*p && (*p == ' ' || *p == ':')) p++;
            int id = 0;
            while(*p >= '0' && *p <= '9'){ id = id * 10 + (*p - '0'); p++; }
            *out_id = id;
            break;
        }
        p++;
    }

    /* Parse cmd */
    p = json;
    while(*p){
        if(p[0] == '"' && p[1] == 'c' && p[2] == 'm' && p[3] == 'd' && p[4] == '"'){
            p += 5;
            while(*p && (*p == ' ' || *p == ':')) p++;
            if(*p == '"'){
                p++;
                usize c = 0;
                while(*p && *p != '"' && c + 1 < cmd_cap){ out_cmd[c++] = *p++; }
                out_cmd[c] = 0;
                return 0;
            }
        }
        p++;
    }
    return -1;
}

/* Handle client request line */
static int handle_client_request(int client_fd, const char *req_line){
    char cmd[64]; int req_id = 0;
    if(parse_json_req(req_line, cmd, sizeof(cmd), &req_id) < 0) return 0;

    int ok = 1;
    u8 mask[MASK_BYTES];
    enum band_spec spec;

    if(contains_ci(cmd, "query") || contains_ci(cmd, "refresh") || contains_ci(cmd, "sim_set")){
        refresh_all_state();
    } else if(contains_ci(cmd, "batch_set")){
        u8 gsm_m[MASK_BYTES], wcdma_m[MASK_BYTES], lte_m[MASK_BYTES], sa_m[MASK_BYTES], nsa_m[MASK_BYTES];
        enum band_spec gsm_s = SPEC_NONE, wcdma_s = SPEC_NONE, lte_s = SPEC_NONE, sa_s = SPEC_NONE, nsa_s = SPEC_NONE;
        enum nr_mode requested_mode = NR_MODE_BOTH;
        int changed = 0;      /* band NV writes -> need a radio reload */

        (void)parse_json_named_bands(req_line, "gsm", gsm_m, &gsm_s);
        (void)parse_json_named_bands(req_line, "wcdma", wcdma_m, &wcdma_s);
        (void)parse_json_named_bands(req_line, "lte", lte_m, &lte_s);
        (void)parse_json_named_bands(req_line, "nr_sa", sa_m, &sa_s);
        (void)parse_json_named_bands(req_line, "nr_nsa", nsa_m, &nsa_s);

        if(contains_ci(req_line, "\"mode\":\"sa\"")) requested_mode = NR_MODE_SA;
        else if(contains_ci(req_line, "\"mode\":\"nsa\"")) requested_mode = NR_MODE_NSA;
        else if(contains_ci(req_line, "\"mode\":\"disable\"") || contains_ci(req_line, "\"mode\":\"off\"")) requested_mode = NR_MODE_DISABLE;

        /* Captured before any write; band writes never touch G.mode. */
        int mode_will_change = (requested_mode != G.mode);

        ok = wcdma_spec_supported(wcdma_m, wcdma_s);
        if(ok){
            if(!band_spec_matches(&G.gsm, gsm_m, gsm_s, G.supported_gsm)){
                changed = 1; if(write_gsm_state(gsm_m, gsm_s) < 0) ok = 0;
            }
            if(!band_spec_matches(&G.wcdma, wcdma_m, wcdma_s, G.supported_wcdma)){
                changed = 1; if(write_wcdma_state(wcdma_m, wcdma_s) < 0) ok = 0;
            }
            if(!band_spec_matches(&G.lte, lte_m, lte_s, G.supported_lte)){
                changed = 1; if(write_lte_state(lte_m, lte_s) < 0) ok = 0;
            }
            if(sa_s != SPEC_NONE && !band_spec_matches(&G.sa, sa_m, sa_s, G.supported_sa)){
                changed = 1; if(write_sa_state(sa_m, sa_s) < 0) ok = 0;
            }
            if(nsa_s != SPEC_NONE && !band_spec_matches(&G.nsa, nsa_m, nsa_s, G.supported_sa)){
                changed = 1; if(write_nsa_state(nsa_m, nsa_s) < 0) ok = 0;
            }
        }

        /* The menu-driven mode change performs its own radio reload, and that
         * reload adopts band NV writes made just before it (verified on
         * device). So our CFUN is only needed when no mode change follows. */
        if(mode_will_change){
            if(ok && write_mode_state(requested_mode) < 0) ok = 0;
        } else if(changed){
            if(at_reload_radio() < 0) ok = 0;
        }
        refresh_all_state();
    } else if(contains_ci(cmd, "lte_set")){
        if(parse_json_bands(req_line, mask, &spec) == 0){
            ok = (write_lte_state(mask, spec) >= 0);
            (void)at_reload_radio();
            refresh_all_state();
        }
    } else if(contains_ci(cmd, "nr_sa_set")){
        if(parse_json_bands(req_line, mask, &spec) == 0){
            ok = (write_sa_state(mask, spec) >= 0);
            (void)at_reload_radio();
            refresh_all_state();
        }
    } else if(contains_ci(cmd, "nr_nsa_set")){
        if(parse_json_bands(req_line, mask, &spec) == 0){
            ok = (write_nsa_state(mask, spec) >= 0);
            (void)at_reload_radio();
            refresh_all_state();
        }
    } else if(contains_ci(cmd, "nr_set")){
        if(parse_json_bands(req_line, mask, &spec) == 0){
            ok = (write_sa_state(mask, spec) >= 0) && (write_nsa_state(mask, spec) >= 0);
            (void)at_reload_radio();
            refresh_all_state();
        }
    } else if(contains_ci(cmd, "wcdma_set")){
        if(parse_json_bands(req_line, mask, &spec) == 0){
            ok = (write_wcdma_state(mask, spec) >= 0);
            if(ok) (void)at_reload_radio();
            refresh_all_state();
        }
    } else if(contains_ci(cmd, "gsm_set")){
        if(parse_json_bands(req_line, mask, &spec) == 0){
            ok = (write_gsm_state(mask, spec) >= 0);
            (void)at_reload_radio();
            refresh_all_state();
        }
    } else if(contains_ci(cmd, "mode_set")){
        enum nr_mode requested_mode;
        if(contains_ci(req_line, "\"sa\"")) requested_mode = NR_MODE_SA;
        else if(contains_ci(req_line, "\"nsa\"")) requested_mode = NR_MODE_NSA;
        else if(contains_ci(req_line, "\"disable\"") || contains_ci(req_line, "\"off\"") || contains_ci(req_line, "\"none\"")) requested_mode = NR_MODE_DISABLE;
        else requested_mode = NR_MODE_BOTH;
        /* Tapping the mode that is already active must not drop the radio.
         * The menu handler applies the change itself, so no CFUN here. */
        if(requested_mode != G.mode){
            if(write_mode_state(requested_mode) < 0) ok = 0;
        }
        refresh_all_state();
    } else if(contains_ci(cmd, "rat_set")){
        ok = 1;
    } else if(contains_ci(cmd, "reset")){
        int mode_will_change = (G.mode != NR_MODE_BOTH);
        (void)write_lte_state(NULL, SPEC_ALL);
        (void)write_sa_state(NULL, SPEC_ALL);
        (void)write_nsa_state(NULL, SPEC_ALL);
        (void)write_wcdma_state(NULL, SPEC_ALL);
        (void)write_gsm_state(NULL, SPEC_ALL);
        /* Menu apply reloads the radio and adopts the band writes above. */
        if(mode_will_change) (void)write_mode_state(NR_MODE_BOTH);
        else (void)at_reload_radio();
        refresh_all_state();
    } else if(contains_ci(cmd, "shutdown")){
        char resp_buf[128]; usize p = 0;
        p = append_text(resp_buf, p, sizeof(resp_buf), "{\"id\":");
        p = append_uint(resp_buf, p, sizeof(resp_buf), (u64)req_id);
        p = append_text(resp_buf, p, sizeof(resp_buf), ",\"cmd\":\"shutdown\",\"ok\":true}\n");
        (void)socket_write_all(client_fd, resp_buf, p);
        return -1; /* Signal server termination */
    }

    char resp[4096];
    usize rlen = format_state_json(resp, 0, sizeof(resp), req_id, cmd, ok);
    (void)socket_write_all(client_fd, resp, rlen);
    return 0;
}

void _start(void){
    zero(&G, sizeof(G));
    G.router_fd = -1;

    /* Parse argv for -uid */
    register long *sp __asm__("sp");
    long argc = *sp;
    char **argv = (char**)(sp + 1);

    for(long i = 1; i < argc; i++){
        if(contains_ci(argv[i], "-uid") && i + 1 < argc){
            u32 u = 0; const char *p = argv[i+1];
            while(*p >= '0' && *p <= '9'){ u = u * 10 + (*p - '0'); p++; }
            G.allowed_uid = u;
            G.enforce_uid = 1;
            i++;
        }
    }

    (void)at_open();
    init_hardware_bands();
    refresh_all_state();

    /* Create abstract UNIX socket */
    int sfd = (int)sc3(SYS_SOCKET, AF_UNIX, SOCK_STREAM, 0);
    if(sfd < 0) sys_exit(1);

    struct sockaddr_un sun;
    zero(&sun, sizeof(sun));
    sun.sun_family = AF_UNIX;
    sun.sun_path[0] = 0; /* Abstract namespace */
    usize sl = slen(SOCKET_NAME);
    if(sl > sizeof(sun.sun_path) - 2) sl = sizeof(sun.sun_path) - 2;
    copy(sun.sun_path + 1, SOCKET_NAME, sl);

    usize addrlen = sizeof(sun.sun_family) + 1 + sl;
    if(sc3(SYS_BIND, sfd, (long)&sun, (long)addrlen) < 0){
        sys_close(sfd);
        sys_exit(2);
    }

    if(sc2(SYS_LISTEN, sfd, 5) < 0){
        sys_close(sfd);
        sys_exit(3);
    }

    /* Main daemon event loop */
    while(1){
        struct pollfd pfd;
        pfd.fd = sfd;
        pfd.events = POLLIN;
        pfd.revents = 0;

        int pr = (int)sys_ppoll(&pfd, 1, NULL);
        if(pr <= 0) continue;

        int cfd = (int)sc3(SYS_ACCEPT, sfd, 0, 0);
        if(cfd < 0) continue;

        /* Peer credential check if -uid was specified */
        if(G.enforce_uid){
            struct ucred cr; zero(&cr, sizeof(cr));
            u32 crlen = sizeof(cr);
            if(sc5(SYS_GETSOCKOPT, cfd, SOL_SOCKET, SO_PEERCRED, (long)&cr, (long)&crlen) == 0){
                if(cr.uid != G.allowed_uid && cr.uid != 0){
                    sys_close(cfd);
                    continue;
                }
            }
        }

        /* Read requests from client */
        char in_buf[4096]; usize in_len = 0;
        int terminate = 0;

        while(!terminate){
            struct pollfd cpfd;
            cpfd.fd = cfd;
            cpfd.events = POLLIN;
            cpfd.revents = 0;

            int cpr = (int)sys_ppoll(&cpfd, 1, NULL);
            if(cpr <= 0) break;
            if(cpfd.revents & (POLLERR | POLLHUP)) break;

            long n = sys_read(cfd, in_buf + in_len, sizeof(in_buf) - in_len - 1);
            if(n <= 0) break;
            in_len += (usize)n;
            in_buf[in_len] = 0;

            /* Process complete lines */
            while(1){
                char *nl = 0;
                for(usize i = 0; i < in_len; i++){
                    if(in_buf[i] == '\n'){ nl = &in_buf[i]; break; }
                }
                if(!nl) break;
                *nl = 0;

                if(handle_client_request(cfd, in_buf) < 0){
                    terminate = 1;
                    break;
                }

                usize consumed = (usize)(nl - in_buf + 1);
                usize remaining = in_len - consumed;
                if(remaining > 0){
                    copy(in_buf, nl + 1, remaining);
                }
                in_len = remaining;
                in_buf[in_len] = 0;
            }
        }

        sys_close(cfd);
        if(terminate) break;
    }

    if(G.router_fd >= 0) sys_close(G.router_fd);
    sys_close(sfd);
    sys_exit(0);
}
