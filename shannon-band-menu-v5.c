/*
 * Shannon Band Menu V5 — Direct AT-NV Backend (menu-driven NR mode; app 4.5.1 parity)
 * Google Tensor / Samsung Shannon Modem Direct NV Band Manager
 * Runs standalone without libc/Termux dependencies on ARM64 Linux / Android.
 */

typedef unsigned long usize;
typedef long isize;
typedef unsigned char u8;
typedef unsigned short u16;
typedef unsigned int u32;
typedef unsigned long u64;
typedef long i64;
typedef int bool;
#define true 1
#define false 0
#define NULL ((void *)0)

#define SYS_OPENAT 56
#define SYS_CLOSE 57
#define SYS_READ 63
#define SYS_WRITE 64
#define SYS_GETDENTS64 61
#define SYS_PPOLL 73
#define SYS_NANOSLEEP 101
#define SYS_CLOCK_GETTIME 113
#define SYS_SETRESUID 147
#define SYS_SETRESGID 149
#define SYS_SETGROUPS 159
#define SYS_EXIT 93

#define AT_FDCWD (-100)
#define O_RDONLY 0
#define O_RDWR 2
#define O_DIRECTORY 0x10000
#define O_CLOEXEC 0x80000
#define POLLIN 0x0001
#define POLLERR 0x0008
#define POLLHUP 0x0010
#define POLLNVAL 0x0020
#define CLOCK_MONOTONIC 1

#define MAX_BAND 2048
#define MASK_BYTES 256
/* Shannon behaves erratically when a RAT is left with an empty manual band
 * list. To take LTE out of service, lock it to a band the network never
 * deploys instead of publishing an empty allow-list. */
#define LTE_DUMMY_BAND 255
#define MAX_OPS 12
#define AT_TIMEOUT_MS 3000
/* NV writes are issued back to back, but the modem intermittently stops
 * answering when CFUN follows the last write immediately. */
#define AT_CFUN_SETTLE_MS 50
#define AT_CFUN_TIMEOUT_MS 4000
#define AT_BUF_SIZE 4096

struct timespec { i64 tv_sec; i64 tv_nsec; };
struct pollfd { int fd; short events; short revents; };

/* ARM64 Linux direct syscall wrappers */
static inline long sc1(long n,long a0){register long x8 __asm__("x8")=n;register long x0 __asm__("x0")=a0;__asm__ volatile("svc 0":"+r"(x0):"r"(x8):"memory");return x0;}
static inline long sc2(long n,long a0,long a1){register long x8 __asm__("x8")=n;register long x0 __asm__("x0")=a0;register long x1 __asm__("x1")=a1;__asm__ volatile("svc 0":"+r"(x0):"r"(x1),"r"(x8):"memory");return x0;}
static inline long sc3(long n,long a0,long a1,long a2){register long x8 __asm__("x8")=n;register long x0 __asm__("x0")=a0;register long x1 __asm__("x1")=a1;register long x2 __asm__("x2")=a2;__asm__ volatile("svc 0":"+r"(x0):"r"(x1),"r"(x2),"r"(x8):"memory");return x0;}
static inline long sc4(long n,long a0,long a1,long a2,long a3){register long x8 __asm__("x8")=n;register long x0 __asm__("x0")=a0;register long x1 __asm__("x1")=a1;register long x2 __asm__("x2")=a2;register long x3 __asm__("x3")=a3;__asm__ volatile("svc 0":"+r"(x0):"r"(x1),"r"(x2),"r"(x3),"r"(x8):"memory");return x0;}
static inline long sc5(long n,long a0,long a1,long a2,long a3,long a4){register long x8 __asm__("x8")=n;register long x0 __asm__("x0")=a0;register long x1 __asm__("x1")=a1;register long x2 __asm__("x2")=a2;register long x3 __asm__("x3")=a3;register long x4 __asm__("x4")=a4;__asm__ volatile("svc 0":"+r"(x0):"r"(x1),"r"(x2),"r"(x3),"r"(x4),"r"(x8):"memory");return x0;}

static long sys_open(const char*p,int flags){return sc4(SYS_OPENAT,AT_FDCWD,(long)p,flags,0);}
static long sys_close(int fd){return sc1(SYS_CLOSE,fd);}
static long sys_read(int fd,void*p,usize n){return sc3(SYS_READ,fd,(long)p,n);}
static long sys_write(int fd,const void*p,usize n){return sc3(SYS_WRITE,fd,(long)p,n);}
static long sys_ppoll(struct pollfd*p,usize n,const struct timespec*t){return sc5(SYS_PPOLL,(long)p,n,(long)t,0,0);}
static long sys_clock_gettime(int id,struct timespec*t){return sc2(SYS_CLOCK_GETTIME,id,(long)t);}
static long sys_nanosleep(const struct timespec*r,struct timespec*l){return sc2(SYS_NANOSLEEP,(long)r,(long)l);}
static void sys_exit(int c){sc1(SYS_EXIT,c);for(;;){}}

/* Memory and string helpers */
static usize slen(const char*s){usize n=0;while(s&&s[n])n++;return n;}
static void zero(void*p,usize n){u8*b=p;while(n--)*b++=0;}
static void copy(void*d,const void*s,usize n){u8*dd=d;const u8*ss=s;while(n--)*dd++=*ss++;}
static void move_bytes(void*d,const void*s,usize n){u8*dd=d;const u8*ss=s;if(dd<ss){usize i;for(i=0;i<n;i++)dd[i]=ss[i];}else{while(n){n--;dd[n]=ss[n];}}}
void *memcpy(void*d,const void*s,usize n){copy(d,s,n);return d;}
void *memset(void*d,int c,usize n){u8*p=d;while(n--)*p++=(u8)c;return d;}
void *memmove(void*d,const void*s,usize n){move_bytes(d,s,n);return d;}
usize strlen(const char*s){return slen(s);}
static int space(char c){return c==' '||c=='\t'||c=='\r'||c=='\n'||c=='\v'||c=='\f';}
static char lowerc(char c){return(c>='A'&&c<='Z')?(char)(c+32):c;}
static int eq(const char*a,const char*b){while(*a&&*b&&*a==*b){a++;b++;}return *a==*b;}
static int eq_ci(const char*a,const char*b){while(*a&&*b&&lowerc(*a)==lowerc(*b)){a++;b++;}return *a==*b;}
static int starts_ci(const char*a,const char*b){while(*b){if(!*a||lowerc(*a)!=lowerc(*b))return 0;a++;b++;}return 1;}
static int contains_ci(const char*s,const char*needle){usize nl=slen(needle),i,j;if(!nl)return 1;for(i=0;s[i];i++){for(j=0;j<nl&&s[i+j]&&lowerc(s[i+j])==lowerc(needle[j]);j++){}if(j==nl)return 1;}return 0;}
static char*trim(char*s){char*e;while(*s&&space(*s))s++;e=s+slen(s);while(e>s&&space(e[-1]))*--e=0;return s;}
static void copy_text(char*d,usize cap,const char*s){usize i=0;if(!cap)return;while(s[i]&&i+1<cap){d[i]=s[i];i++;}d[i]=0;}
static usize append_text(char*d,usize pos,usize cap,const char*s){while(*s&&pos+1<cap)d[pos++]=*s++;d[pos]=0;return pos;}

static int write_all(int fd,const void*p,usize n){const u8*b=p;while(n){long r=sys_write(fd,b,n);if(r<=0)return-1;b+=(usize)r;n-=(usize)r;}return 0;}
static void out(const char*s){(void)write_all(1,s,slen(s));}
static void err(const char*s){(void)write_all(2,s,slen(s));}
static void outnum(u32 v){char b[12];int i=0,j;if(!v){out("0");return;}while(v){b[i++]=(char)('0'+v%10);v/=10;}for(j=i-1;j>=0;j--)sys_write(1,&b[j],1);}

static i64 now_ms(void){struct timespec t;if(sys_clock_gettime(CLOCK_MONOTONIC,&t)<0)return 0;return t.tv_sec*1000+t.tv_nsec/1000000;}
static void sleep_ms(u32 ms){struct timespec r={(i64)(ms/1000),(i64)(ms%1000)*1000000};while(sys_nanosleep(&r,&r)<0){}}

/* Bitmask helpers */
static void mask_set(u8*m,u32 b){u32 x=b-1;m[x/8]|=(u8)(1u<<(x%8));}
static int mask_has(const u8*m,u32 b){u32 x=b-1;return(m[x/8]&(u8)(1u<<(x%8)))!=0;}
static void mask_clear(u8*m,u32 b){u32 x=b-1;m[x/8]&=(u8)~(1u<<(x%8));}
static int mask_any(const u8*m){int i;for(i=0;i<MASK_BYTES;i++)if(m[i])return 1;return 0;}
static u32 mask_count(const u8*m){u32 b,n=0;for(b=1;b<=MAX_BAND;b++)if(mask_has(m,b))n++;return n;}
static int mask_equal(const u8*a,const u8*b){int i;for(i=0;i<MASK_BYTES;i++)if(a[i]!=b[i])return 0;return 1;}
static int mask_subset(const u8*m,const u8*supported){u32 b;for(b=1;b<=MAX_BAND;b++)if(mask_has(m,b)&&!mask_has(supported,b))return 0;return 1;}
static void print_mask(const u8*m,u32 max,const char*pre){u32 b;int first=1;for(b=1;b<=max;b++)if(mask_has(m,b)){if(!first)out(",");out(pre);outnum(b);first=0;}if(first)out("none");out("\n");}
static void print_gsm_mask(const u8*m){int first=1;if(mask_has(m,850)){out(first?"GSM850":",GSM850");first=0;}if(mask_has(m,900)){out(first?"GSM900":",GSM900");first=0;}if(mask_has(m,1800)){out(first?"DCS1800":",DCS1800");first=0;}if(mask_has(m,1900)){out(first?"PCS1900":",PCS1900");first=0;}if(first)out("none");out("\n");}

/* Device paths */
static const char ROUTER_PATH[]="/dev/umts_router";
/* The NR-mode menu is rendered by the modem itself. Writing NR.CONFIG.MODE
 * directly stores the value but never applies it - the modem's own menu
 * handler must perform the change. Drive that menu over the DM channel with
 * keypresses, then verify over the AT channel.
 * See agents/NR_MODE_APPLY_PROBLEM.md. */
static const char DM_PATH[]="/dev/umts_dm0";
#define MENU_KEY_GAP_MS 50
#define MENU_VERIFY_TRIES 12
#define MENU_VERIFY_GAP_MS 100
#define MENU_BACK 0x5c

/* Data structures */
struct family_state {
    u8 mask[MASK_BYTES];
    int manual_enabled;
    int valid;
};

struct app_state {
    struct family_state lte;
    struct family_state nsa;
    struct family_state sa;
    struct family_state wcdma;
    struct family_state gsm;
    u8 supported_lte[MASK_BYTES];
    u8 supported_wcdma[MASK_BYTES];
    u8 supported_gsm[MASK_BYTES];
    u8 supported_sa[MASK_BYTES];
    u32 supported_sa_count;
    int mode;           /* 0: SA+NSA, 1: NSA only, 2: SA only, 3: Disabled/LTE only */
    int mode_known;
    int vonr_state;     /* 1: ON, 0: OFF, -1: Default (0xFF), -2: unknown */
    int nsg_running;
    char status[256];
};

static struct app_state S;
static int router_fd = -1;

static void setstatus(const char*s){copy_text(S.status,sizeof(S.status),s);}

/* AT transport engine over /dev/umts_router */
static int at_open(void){
    if(router_fd >= 0) return 0;
    router_fd = (int)sys_open(ROUTER_PATH, O_RDWR | O_CLOEXEC);
    if(router_fd < 0){
        err("Failed to open /dev/umts_router. Please ensure root privileges.\n");
        return -1;
    }
    return 0;
}

static void at_close(void){
    if(router_fd >= 0){
        sys_close(router_fd);
        router_fd = -1;
    }
}

static void at_drain(int timeout_ms){
    struct pollfd pfd = {router_fd, POLLIN, 0};
    struct timespec ts = {(i64)(timeout_ms/1000), (i64)(timeout_ms%1000)*1000000};
    u8 buf[512];
    while(sys_ppoll(&pfd, 1, &ts) > 0 && (pfd.revents & POLLIN)){
        long n = sys_read(router_fd, buf, sizeof(buf));
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

    /* Write command followed by \r */
    if(write_all(router_fd, cmd, cmd_len) < 0) return -1;
    if(cmd_len == 0 || cmd[cmd_len-1] != '\r'){
        if(write_all(router_fd, "\r", 1) < 0) return -1;
    }

    if(resp && resp_cap > 0) resp[0] = 0;
    deadline = now_ms() + timeout_ms;

    while(now_ms() < deadline){
        int left_ms = (int)(deadline - now_ms());
        if(left_ms <= 0) break;

        pfd.fd = router_fd;
        pfd.events = POLLIN;
        pfd.revents = 0;
        ts.tv_sec = left_ms / 1000;
        ts.tv_nsec = (left_ms % 1000) * 1000000;

        int pr = (int)sys_ppoll(&pfd, 1, &ts);
        if(pr < 0) return -1;
        if(pr == 0) continue;

        if(pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) return -1;
        if(pfd.revents & POLLIN){
            char chunk[256];
            long n = sys_read(router_fd, chunk, sizeof(chunk) - 1);
            if(n <= 0) break;
            chunk[n] = 0;

            if(resp && resp_cap > 0 && resp_len + 1 < resp_cap){
                usize room = resp_cap - resp_len - 1;
                usize take = (usize)n < room ? (usize)n : room;
                copy(resp + resp_len, chunk, take);
                resp_len += take;
                resp[resp_len] = 0;
            }

            if(contains_ci(chunk, "OK") || (resp && contains_ci(resp, "OK"))) {
                return 0;
            }
            if(contains_ci(chunk, "ERROR") || (resp && contains_ci(resp, "ERROR"))) {
                return -1;
            }
        }
    }

    return (resp && contains_ci(resp, "OK")) ? 0 : -1;
}

/* Hex parsing and formatting helpers */
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

static void format_hex_csv(const u8 *bytes, usize count, char *out_str, usize out_cap){
    static const char hex_digits[] = "0123456789ABCDEF";
    usize pos = 0;
    usize i;
    if(!out_cap) return;
    out_str[0] = 0;
    for(i = 0; i < count; i++){
        if(i > 0 && pos + 1 < out_cap) out_str[pos++] = ',';
        if(pos + 2 < out_cap){
            out_str[pos++] = hex_digits[(bytes[i] >> 4) & 0x0F];
            out_str[pos++] = hex_digits[bytes[i] & 0x0F];
        }
    }
    out_str[pos] = 0;
}

/* Direct GOOGGETNV and GOOGSETNV helpers */
static int googgetnv(const char *name, int index, u8 *out_bytes, usize max_bytes, usize *out_count){
    char cmd[128], resp[1024];
    usize p = 0;
    p = append_text(cmd, p, sizeof(cmd), "AT+GOOGGETNV=\"");
    p = append_text(cmd, p, sizeof(cmd), name);
    p = append_text(cmd, p, sizeof(cmd), "\",");
    {
        char numbuf[12];
        int ni = 0;
        int idx = index;
        if(!idx) numbuf[ni++] = '0';
        else while(idx){ numbuf[ni++] = (char)('0' + idx % 10); idx /= 10; }
        while(ni > 0 && p + 1 < sizeof(cmd)) cmd[p++] = numbuf[--ni];
    }
    cmd[p] = 0;

    if(at_exec(cmd, resp, sizeof(resp), AT_TIMEOUT_MS) < 0) return -1;

    /* Find +GOOGGETNV: response line */
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
        char numbuf[12];
        int ni = 0;
        int idx = index;
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

/* Array query helper using format 1 (returns all elements in one response) */
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

/* ========================================================================= */
/* RAT Backends (LTE, NR SA, NR NSA, NR Mode, Supported Band Validation)     */
/* ========================================================================= */

/* Supported NR Bands Discovery */
static void supp_nr_cb(int idx, const u8 *bytes, usize len, void *ctx){
    (void)idx; (void)ctx;
    if(len >= 2){
        u16 b = (u16)(bytes[0] | ((u16)bytes[1] << 8));
        if(b >= 1 && b <= MAX_BAND){
            mask_set(S.supported_sa, (u32)b);
            S.supported_sa_count++;
        }
    }
}

static void init_hardware_bands(void){
    static const u16 def_lte[] = {1,2,3,4,5,7,8,12,13,14,17,18,19,20,25,26,28,29,30,32,38,39,40,41,46,48,66,71};
    static const u16 def_wcdma[] = {1,2,4,5,8};
    static const u16 def_gsm[] = {850,900,1800,1900};
    usize i;

    zero(S.supported_lte, sizeof(S.supported_lte));
    for(i=0;i<sizeof(def_lte)/sizeof(def_lte[0]);i++) mask_set(S.supported_lte,def_lte[i]);

    /* NSG-confirmed user-selectable set. B6/B19 in ds_ defaults are internal. */
    zero(S.supported_wcdma, sizeof(S.supported_wcdma));
    for(i=0;i<sizeof(def_wcdma)/sizeof(def_wcdma[0]);i++) mask_set(S.supported_wcdma,def_wcdma[i]);

    zero(S.supported_gsm, sizeof(S.supported_gsm));
    for(i=0;i<sizeof(def_gsm)/sizeof(def_gsm[0]);i++) mask_set(S.supported_gsm,def_gsm[i]);

    zero(S.supported_sa, sizeof(S.supported_sa));
    S.supported_sa_count = 0;
    (void)googgetnv_array("!NRRRC.SUPPORTED_NR_BAND_LIST", supp_nr_cb, NULL);

    if(!S.supported_sa_count){
        static const u16 def_nr[] = {1,2,3,5,7,8,12,20,25,26,28,29,30,38,40,41,48,66,70,71,75,77,78,79,257,258,260,261};
        for(i = 0; i < sizeof(def_nr)/sizeof(def_nr[0]); i++){
            mask_set(S.supported_sa, def_nr[i]);
            S.supported_sa_count++;
        }
    }
}

/* LTE Manual Band Selection */
static void lte_bitmap_cb(int idx, const u8 *bytes, usize len, void *ctx){
    (void)ctx;
    if(idx >= 0 && idx < 4 && len >= 8){
        int b;
        for(b = 0; b < 8; b++){
            S.lte.mask[idx * 8 + b] = bytes[b];
        }
    }
}

static int read_lte_state(void){
    u8 enb_bytes[1];
    usize enb_len = 0;
    zero(&S.lte, sizeof(S.lte));

    if(googgetnv("!SAEL3.Manual.Band.Select Enb/ Dis", 0, enb_bytes, sizeof(enb_bytes), &enb_len) < 0){
        return -1;
    }
    S.lte.manual_enabled = (enb_len > 0 && enb_bytes[0] == 1);
    if(S.lte.manual_enabled){
        if(googgetnv_array("!SAEL3.Manual.Enabled.RFBands.BitMap", lte_bitmap_cb, NULL) < 0) return -1;
        /* The dummy band only marks "LTE RAT off"; it is never a user selection. */
        mask_clear(S.lte.mask, LTE_DUMMY_BAND);
    } else {
        copy(S.lte.mask, S.supported_lte, MASK_BYTES);
    }
    S.lte.valid = 1;
    return 0;
}

enum band_spec { SPEC_LIST, SPEC_ALL, SPEC_NONE };

static int write_lte_state(const u8 *mask, enum band_spec spec){
    u8 enb[1];
    if(spec == SPEC_ALL){
        enb[0] = 0; /* Auto / Normal selection */
        if(googsetnv("!SAEL3.Manual.Band.Select Enb/ Dis", 0, enb, 1) < 0) return -1;
        return 0;
    }

    /* Build 256-bit bitmap */
    u8 full_bitmap[32];
    zero(full_bitmap, sizeof(full_bitmap));
    if(spec == SPEC_LIST && mask && mask_any(mask)){
        copy(full_bitmap, mask, 32);
        mask_clear(full_bitmap, LTE_DUMMY_BAND);
    } else {
        /* RAT off. An empty manual list makes the modem behave erratically,
         * so lock LTE to a band that is never deployed instead. */
        u32 bit = LTE_DUMMY_BAND - 1;
        full_bitmap[bit / 8] |= (u8)(1u << (bit % 8));
    }

    /* Write 4 bitmap indices first */
    int i;
    for(i = 0; i < 4; i++){
        if(googsetnv("!SAEL3.Manual.Enabled.RFBands.BitMap", i, &full_bitmap[i * 8], 8) < 0) return -1;
    }

    /* Enable manual selection */
    enb[0] = 1;
    if(googsetnv("!SAEL3.Manual.Band.Select Enb/ Dis", 0, enb, 1) < 0) return -1;
    return 0;
}

/* 5G NR SA Manual Band Selection */
struct sa_cb_ctx { u16 count; };
static void sa_list_cb(int idx, const u8 *bytes, usize len, void *ctx){
    struct sa_cb_ctx *c = (struct sa_cb_ctx*)ctx;
    if(idx < (int)c->count && len >= 2){
        u16 b = (u16)(bytes[0] | ((u16)bytes[1] << 8));
        if(b >= 1 && b <= MAX_BAND){
            mask_set(S.sa.mask, (u32)b);
        }
    }
}

static int read_sa_state(void){
    u8 cnt_bytes[2];
    usize cnt_len = 0;
    zero(&S.sa, sizeof(S.sa));

    if(S.mode_known && (S.mode == 1 || S.mode == 3)){
        S.sa.valid = 1;
        return 0;
    }

    if(googgetnv("!NRRRC.NUM_MANUAL_NR_BAND_LIST", 0, cnt_bytes, sizeof(cnt_bytes), &cnt_len) < 0 || cnt_len < 2){
        return -1;
    }

    struct sa_cb_ctx ctx;
    ctx.count = (u16)(cnt_bytes[0] | ((u16)cnt_bytes[1] << 8));
    if(ctx.count > 60) ctx.count = 60;
    if(ctx.count > 0 && googgetnv_array("!NRRRC.MANUAL_NR_BAND_LIST", sa_list_cb, &ctx) < 0) return -1;

    S.sa.manual_enabled = ctx.count > 0;
    if(!mask_any(S.sa.mask)){
        copy(S.sa.mask, S.supported_sa, MASK_BYTES);
        S.sa.manual_enabled = 0;
    }

    S.sa.valid = 1;
    return 0;
}

static int write_sa_state(const u8 *mask, enum band_spec spec){
    u16 bands[60];
    u16 count = 0;
    u16 previous_count = 0;
    /* NR is switched off through NR.CONFIG.MODE alone. Keep the stored band
     * list intact so it survives an NR off/on cycle. */
    if(spec == SPEC_NONE) return 0;
    u8 previous_bytes[2];
    usize previous_len = 0;

    if(googgetnv("!NRRRC.NUM_MANUAL_NR_BAND_LIST", 0, previous_bytes, sizeof(previous_bytes), &previous_len) == 0 && previous_len >= 2){
        previous_count = (u16)(previous_bytes[0] | ((u16)previous_bytes[1] << 8));
        if(previous_count > 60) previous_count = 60;
    }

    if(spec == SPEC_ALL){
        u32 b;
        for(b = 1; b <= MAX_BAND && count < 60; b++){
            if(mask_has(S.supported_sa, b)){
                bands[count++] = (u16)b;
            }
        }
    } else if(spec == SPEC_LIST && mask){
        u32 b;
        for(b = 1; b <= MAX_BAND && count < 60; b++){
            if(mask_has(mask, b)){
                bands[count++] = (u16)b;
            }
        }
    }

    /* Batch write array elements first */
    u16 i;
    for(i = 0; i < count; i++){
        u8 val[2];
        val[0] = (u8)(bands[i] & 0xFF);
        val[1] = (u8)((bands[i] >> 8) & 0xFF);
        if(googsetnv("!NRRRC.MANUAL_NR_BAND_LIST", (int)i, val, 2) < 0) return -1;
    }

    /* Remove stale entries from a previous longer selection. */
    for(i = count; i < previous_count; i++){
        u8 val[2] = {0, 0};
        if(googsetnv("!NRRRC.MANUAL_NR_BAND_LIST", (int)i, val, 2) < 0) return -1;
    }

    /* Write count last */
    u8 cnt_bytes[2];
    cnt_bytes[0] = (u8)(count & 0xFF);
    cnt_bytes[1] = (u8)((count >> 8) & 0xFF);
    if(googsetnv("!NRRRC.NUM_MANUAL_NR_BAND_LIST", 0, cnt_bytes, 2) < 0) return -1;

    return 0;
}

/* 5G NR NSA Manual Band Selection */
struct nsa_cb_ctx { u16 count; int stop_at_zero; int stopped; };
static void nsa_list_cb(int idx, const u8 *bytes, usize len, void *ctx){
    struct nsa_cb_ctx *c = (struct nsa_cb_ctx*)ctx;
    if(!c->stopped && idx >= 0 && idx < (int)c->count && len >= 2){
        u16 b = (u16)(bytes[0] | ((u16)bytes[1] << 8));
        if(c->stop_at_zero && b == 0){ c->stopped = 1; return; }
        if(b >= 1 && b <= MAX_BAND){
            mask_set(S.nsa.mask, (u32)b);
        }
    }
}

static int read_nsa_state(void){
    u8 enb_bytes[1];
    usize enb_len = 0;
    zero(&S.nsa, sizeof(S.nsa));

    if(S.mode_known && (S.mode == 2 || S.mode == 3)){
        S.nsa.valid = 1;
        return 0;
    }

    if(googgetnv("!LTE.NR Manual Band Enable/Disable", 0, enb_bytes, sizeof(enb_bytes), &enb_len) < 0 || enb_len == 0) return -1;
    S.nsa.manual_enabled = enb_bytes[0] != 0;
    if(!S.nsa.manual_enabled){
        copy(S.nsa.mask, S.supported_sa, MASK_BYTES);
        S.nsa.valid = 1;
        return 0;
    }

    /* Direct LTE NSA list is authoritative and terminates at the first zero. */
    struct nsa_cb_ctx ctx = {60, 1, 0};
    if(googgetnv_array("!LTE.NR Manual Band List", nsa_list_cb, &ctx) < 0){
        /* Compatibility fallback for modem builds without the direct list. */
        u8 cnt_bytes[2]; usize cnt_len = 0;
        if(googgetnv("!NRRRC_NUM_SVC_MENU_NSA_NR_BAND_LIST", 0, cnt_bytes, sizeof(cnt_bytes), &cnt_len) < 0 || cnt_len < 2) return -1;
        ctx.count = (u16)(cnt_bytes[0] | ((u16)cnt_bytes[1] << 8));
        if(ctx.count > 60) ctx.count = 60;
        ctx.stop_at_zero = 0;
        ctx.stopped = 0;
        if(ctx.count > 0 && googgetnv_array("!NRRRC_SVC_MENU_NSA_NR_BAND_LIST", nsa_list_cb, &ctx) < 0) return -1;
    }

    S.nsa.valid = 1;
    return 0;
}

static int write_nsa_state(const u8 *mask, enum band_spec spec){
    u16 bands[60];
    u16 count = 0;
    /* See write_sa_state: NR off is expressed by NR.CONFIG.MODE only. */
    if(spec == SPEC_NONE) return 0;

    if(spec == SPEC_ALL){
        u32 b;
        for(b = 1; b <= MAX_BAND && count < 60; b++){
            if(mask_has(S.supported_sa, b)){
                bands[count++] = (u16)b;
            }
        }
    } else if(spec == SPEC_LIST && mask){
        u32 b;
        for(b = 1; b <= MAX_BAND && count < 60; b++){
            if(mask_has(mask, b)){
                bands[count++] = (u16)b;
            }
        }
    }

    /* Write only the authoritative direct list. */
    u16 i;
    for(i = 0; i < count; i++){
        u8 val[2];
        val[0] = (u8)(bands[i] & 0xFF);
        val[1] = (u8)((bands[i] >> 8) & 0xFF);
        if(googsetnv("!LTE.NR Manual Band List", (int)i, val, 2) < 0) return -1;
    }

    /* Null terminate and publish the manual-enable flag. */
    u8 term[2] = {0, 0};
    if(googsetnv("!LTE.NR Manual Band List", (int)count, term, 2) < 0) return -1;
    u8 enb[1] = { (u8)(count ? 1 : 0) };
    return googsetnv("!LTE.NR Manual Band Enable/Disable", 0, enb, 1);
}

/* NR Operating Mode (NR.CONFIG.MODE) */
static int read_nr_mode(void){
    u8 mode_bytes[1];
    usize mlen = 0;
    S.mode_known = 0;

    if(googgetnv("NR.CONFIG.MODE", 0, mode_bytes, sizeof(mode_bytes), &mlen) == 0 && mlen >= 1){
        if(mode_bytes[0] == 0x11){
            S.mode = 0; /* SA + NSA */
            S.mode_known = 1;
        } else if(mode_bytes[0] == 0x01){
            S.mode = 1; /* NSA only */
            S.mode_known = 1;
        } else if(mode_bytes[0] == 0x10){
            S.mode = 2; /* SA only */
            S.mode_known = 1;
        } else if(mode_bytes[0] == 0x00){
            S.mode = 3; /* LTE only / disabled */
            S.mode_known = 1;
        } else {
            S.mode = 0;
            S.mode_known = 1;
        }
        return 0;
    }
    return -1;
}

/* ---- Modem service-menu driver (DM channel) ---- */
static const u8 DM_PROBE_ENABLE[]={0x7f,0x12,0,0,0x0f,0,0,0,0xa0,0,0x52,0,0,0,0,1,0,0,0,0x7e};
static const u8 DM_STREAM_STOP[] ={0x7f,0x12,0,0,0x0f,0,0,0,0xa0,0,0x90,0,0,0,0,0,0,0,0,0x7e};
static const u8 DM_PROBE_QUERY[] ={0x7f,0x16,0,0,0x13,0,0,0,0xa0,0,0,0,0,0,0,0x34,0xdc,0x12,0xfe,0,0,0,0xc0,0x7e};
static const u8 DM_I1_72[]       ={0x7f,0x0e,0,0,0x0b,0,0,0,0xa0,0,0x72,0,0,0,0,0x7e};
static const u8 DM_I1_06[]       ={0x7f,0x10,0,0,0x0d,0,0,0,0xa0,0,0x06,0,0,0,0,3,3,0x7e};
static const u8 DM_I1_A0[]       ={0x7f,0x1b,0,0,0x18,0,0,0,0xa0,0,0xa0,0,0,0,0,0,0x3e,0,0,0,
                                   0xff,0xff,0xff,0xff,0xff,0xff,0xff,0x3f,0x7e};
static const u8 DM_FORCINGS_ON[] ={0x7f,0x12,0,0,0x0f,0,0,0,0xa0,0,0x9a,0,0,0,0,1,1,0x16,0x10,0x7e};

static int dm_simple(int fd,u8 cmd){
    u8 f[17]={0x7f,0x0f,0,0,0x0c,0,0,0,0xa0,0,0,0,0,0,0,0xff,0x7e};
    f[10]=cmd;
    return write_all(fd,f,sizeof(f));
}
static int dm_key(int fd,u8 key){
    u8 f[17]={0x7f,0x0f,0,0,0x0c,0,0,0,0xa0,0x08,0x09,0,0,0,0,0,0x7e};
    f[15]=key;
    if(write_all(fd,f,sizeof(f))<0)return-1;
    sleep_ms(MENU_KEY_GAP_MS);
    return 0;
}
static int dm_open_session(void){
    int fd=(int)sys_open(DM_PATH,O_RDWR|O_CLOEXEC);
    if(fd<0)return-1;
    if(write_all(fd,DM_PROBE_ENABLE,sizeof(DM_PROBE_ENABLE))<0)goto fail;
    if(write_all(fd,DM_PROBE_QUERY,sizeof(DM_PROBE_QUERY))<0)goto fail;
    if(write_all(fd,DM_I1_72,sizeof(DM_I1_72))<0)goto fail;
    if(write_all(fd,DM_I1_06,sizeof(DM_I1_06))<0)goto fail;
    if(dm_simple(fd,0x10)<0||dm_simple(fd,0x20)<0||dm_simple(fd,0x30)<0)goto fail;
    if(dm_simple(fd,0x40)<0||dm_simple(fd,0x44)<0)goto fail;
    if(write_all(fd,DM_I1_A0,sizeof(DM_I1_A0))<0)goto fail;
    if(dm_simple(fd,0x12)<0||dm_simple(fd,0x22)<0||dm_simple(fd,0x32)<0)goto fail;
    if(dm_simple(fd,0x42)<0||dm_simple(fd,0x46)<0)goto fail;
    if(write_all(fd,DM_STREAM_STOP,sizeof(DM_STREAM_STOP))<0)goto fail;
    if(write_all(fd,DM_FORCINGS_ON,sizeof(DM_FORCINGS_ON))<0)goto fail;
    return fd;
fail:
    sys_close(fd);
    return-1;
}
/* Deliberately does NOT send FORCINGS_DISABLE: that frame is global modem
 * state and would tear down a concurrently running NSG session. Two Back
 * presses return the shared page cursor to root instead. */
static u8 menu_key_for_mode(int mode){
    if(mode==0)return '4';   /* SA+NSA */
    if(mode==1)return '3';   /* NSA    */
    if(mode==2)return '5';   /* SA     */
    return '2';              /* disable / LTE only */
}
static int menu_set_nr_mode(int mode){
    int fd=dm_open_session();
    if(fd<0)return-1;
    if(dm_key(fd,'1')<0)goto fail;
    if(dm_key(fd,'3')<0)goto fail;
    if(dm_key(fd,menu_key_for_mode(mode))<0)goto fail;
    if(dm_key(fd,MENU_BACK)<0)goto fail;
    if(dm_key(fd,MENU_BACK)<0)goto fail;
    sys_close(fd);
    return 0;
fail:
    sys_close(fd);
    return-1;
}

static int write_nr_mode_nv_unused(int mode){
    u8 mode_bytes[1];
    if(mode == 0){
        mode_bytes[0] = 0x11; /* SA + NSA */
    } else if(mode == 1){
        mode_bytes[0] = 0x01; /* NSA only */
    } else if(mode == 2){
        mode_bytes[0] = 0x10; /* SA only */
    } else {
        mode_bytes[0] = 0x00; /* Disable NR */
    }
    return googsetnv("NR.CONFIG.MODE", 0, mode_bytes, 1);
}

/* NR mode is applied by the modem's menu handler. The NV lands a variable
 * moment after the keypresses, so poll the AT channel rather than trusting a
 * single immediate readback. */
static int write_nr_mode(int mode){
    int attempt;
    (void)write_nr_mode_nv_unused; /* direct NV write is intentionally unused */
    if(menu_set_nr_mode(mode)<0)return-1;
    for(attempt=0;attempt<MENU_VERIFY_TRIES;attempt++){
        if(read_nr_mode()==0 && S.mode_known && S.mode==mode)return 0;
        sleep_ms(MENU_VERIFY_GAP_MS);
    }
    return-1;
}

/* WCDMA Manual Band Selection */
struct wcdma_cb_ctx { u32 count; u8 disabled[4]; };

static int wcdma_disabled(const u8 *disabled, u32 band){
    u32 bit;
    if(band < 1 || band > 19) return 1;
    bit = band - 1;
    return (disabled[bit / 8] & (u8)(1u << (bit % 8))) != 0;
}

static void wcdma_list_cb(int idx, const u8 *bytes, usize len, void *opaque){
    struct wcdma_cb_ctx *ctx = (struct wcdma_cb_ctx*)opaque;
    u32 band;
    if(!ctx || idx < 0 || (u32)idx >= ctx->count || len == 0) return;
    band = bytes[0];
    if(band >= 1 && band <= 19 && mask_has(S.supported_wcdma, band) && !wcdma_disabled(ctx->disabled, band)){
        mask_set(S.wcdma.mask, band);
    }
}

static int read_wcdma_state(void){
    u8 max_bytes[1], dis_bytes[4];
    usize mlen = 0, dlen = 0;
    zero(&S.wcdma, sizeof(S.wcdma));

    if(googgetnv("UL3.Etc.max_band", 0, max_bytes, sizeof(max_bytes), &mlen) < 0) return -1;
    if(googgetnv("UL3.Etc.disabled_band", 0, dis_bytes, sizeof(dis_bytes), &dlen) < 0) return -1;

    if(mlen == 0 || dlen < 4) return -1;
    if(dis_bytes[0] == 0xFF && dis_bytes[1] == 0xFF && dis_bytes[2] == 0xFF && dis_bytes[3] == 0xFF){
        S.wcdma.manual_enabled = 1;
        S.wcdma.valid = 1;
        return 0;
    }

    if(max_bytes[0] == 0xFF){
        /* Compatibility with older builds that encoded auto as FF. */
        copy(S.wcdma.mask, S.supported_wcdma, MASK_BYTES);
    } else if(max_bytes[0] >= 1 && max_bytes[0] <= 19){
        struct wcdma_cb_ctx ctx;
        ctx.count = max_bytes[0];
        copy(ctx.disabled, dis_bytes, 4);
        if(googgetnv_array("UL3.Etc.Storing Last Camped Bands", wcdma_list_cb, &ctx) < 0) return -1;
    } else if(max_bytes[0] != 0){
        return -1;
    }

    S.wcdma.manual_enabled = !mask_equal(S.wcdma.mask, S.supported_wcdma);
    S.wcdma.valid = 1;
    return 0;
}

static int write_wcdma_state(const u8 *mask, enum band_spec spec){
    const u8 *selected;
    u32 count,band;
    int list_index = 0;
    u8 disabled_all[4] = {0xFF,0xFF,0xFF,0xFF};
    u8 disabled_none[4] = {0,0,0,0};

    if(spec == SPEC_NONE){
        /* Gate WCDMA off without destroying the saved count/list. */
        return googsetnv("UL3.Etc.disabled_band", 0, disabled_all, 4);
    }

    selected = spec == SPEC_ALL ? S.supported_wcdma : mask;
    count = selected ? mask_count(selected) : 0;
    if(count == 0 || count > 19 || !mask_subset(selected, S.supported_wcdma)) return -1;

    if(googsetnv("UL3.Etc.disabled_band", 0, disabled_all, 4) < 0) return -1;
    for(band=1;band<=19;band++){
        if(mask_has(selected,band)){
            u8 value[1] = {(u8)band};
            if(googsetnv("UL3.Etc.Storing Last Camped Bands", list_index++, value, 1) < 0) return -1;
        }
    }
    {
        u8 count_value[1] = {(u8)count};
        if(googsetnv("UL3.Etc.max_band", 0, count_value, 1) < 0) return -1;
    }
    return googsetnv("UL3.Etc.disabled_band", 0, disabled_none, 4);
}

/* VoNR Control (OEM_GFEATURE_QMS_VONR_SAVE_AP_VALUE & OMC.BASED.VONR.PLMN.ENABLE) */
static int read_vonr_state(void){
    u8 ap_val[1];
    usize len = 0;
    S.vonr_state = -2; /* unknown */

    if(googgetnv("OEM_GFEATURE_QMS_VONR_SAVE_AP_VALUE", 0, ap_val, sizeof(ap_val), &len) == 0 && len >= 1){
        if(ap_val[0] == 0x01) S.vonr_state = 1;       /* Forced ON */
        else if(ap_val[0] == 0x00) S.vonr_state = 0;  /* Forced OFF */
        else if(ap_val[0] == 0xFF) S.vonr_state = -1; /* Default / Carrier Policy */
        else S.vonr_state = (int)ap_val[0];
        return 0;
    }
    return -1;
}

static int write_vonr_state(int state){
    u8 ap_val[1], plmn_val[1];
    if(state == 1){
        ap_val[0] = 0x01;
        plmn_val[0] = 0x01;
    } else if(state == 0){
        ap_val[0] = 0x00;
        plmn_val[0] = 0x00;
    } else {
        ap_val[0] = 0xFF; /* Default */
        plmn_val[0] = 0x00;
    }

    if(googsetnv("OEM_GFEATURE_QMS_VONR_SAVE_AP_VALUE", 0, ap_val, 1) < 0) return -1;
    return googsetnv("OMC.BASED.VONR.PLMN.ENABLE", 0, plmn_val, 1);
}

/* GSM / 2G Manual Band Selection (GL3.Edge_Band_Config & GL3.Operator Specific Band) */
static int read_gsm_state(void){
    u8 cfg[1];
    usize len = 0;
    zero(&S.gsm, sizeof(S.gsm));

    if(googgetnv("GL3.Edge_Band_Config", 0, cfg, sizeof(cfg), &len) == 0 && len >= 1){
        u8 val = cfg[0];
        if(val & 0x01) mask_set(S.gsm.mask, 850);
        if(val & 0x02) mask_set(S.gsm.mask, 900);
        if(val & 0x04) mask_set(S.gsm.mask, 1800);
        if(val & 0x08) mask_set(S.gsm.mask, 1900);
        S.gsm.manual_enabled = !mask_equal(S.gsm.mask, S.supported_gsm);
        S.gsm.valid = 1;
        return 0;
    }
    return -1;
}

static int write_gsm_state(const u8 *mask, enum band_spec spec){
    u8 val[1] = {0};
    if(spec == SPEC_ALL){
        val[0] = 0x0F; /* All 4 Quad-band frequencies */
    } else if(spec == SPEC_NONE){
        val[0] = 0x00; /* Disable all GSM */
    } else if(spec == SPEC_LIST && mask){
        if(mask_has(mask, 850)) val[0] |= 0x01;
        if(mask_has(mask, 900)) val[0] |= 0x02;
        if(mask_has(mask, 1800)) val[0] |= 0x04;
        if(mask_has(mask, 1900)) val[0] |= 0x08;
    }

    if(googsetnv("GL3.Edge_Band_Config", 0, val, 1) < 0) return -1;
    return googsetnv("GL3.Operator Specific Band", 0, val, 1);
}

/* Full state refresh */
static int refresh_all_state(void){
    int ok = 1;
    /* Mode must be known before reading mode-inactive SA/NSA families. */
    if(read_nr_mode() < 0) ok = 0;
    if(read_lte_state() < 0) ok = 0;
    if(read_sa_state() < 0) ok = 0;
    if(read_nsa_state() < 0) ok = 0;
    if(read_wcdma_state() < 0) ok = 0;
    if(read_gsm_state() < 0) ok = 0;
    if(read_vonr_state() < 0) ok = 0;
    return ok;
}

/* ========================================================================= */
/* UI and Command Parser                                                     */
/* ========================================================================= */

struct linux_dirent64{u64 ino;i64 off;u16 reclen;u8 type;char name[];};
static int is_digits(const char*s){int any=0;while(*s){if(*s<'0'||*s>'9')return 0;any=1;s++;}return any;}
static int read_small(const char*path,char*b,usize cap){int fd=(int)sys_open(path,O_RDONLY|O_CLOEXEC);long n;if(fd<0)return 0;n=sys_read(fd,b,cap-1);sys_close(fd);if(n<=0)return 0;b[n]=0;return 1;}
static int detect_nsg_running(void){
    int dfd=(int)sys_open("/proc",O_RDONLY|O_DIRECTORY|O_CLOEXEC);u8 buf[8192];long n;usize off;int found=0;
    if(dfd<0)return 0;
    while(!found&&(n=sc3(SYS_GETDENTS64,dfd,(long)buf,sizeof(buf)))>0){
        off=0;
        while(off<(usize)n){
            struct linux_dirent64*d=(struct linux_dirent64*)(buf+off);
            if(d->reclen<20)break;
            if(is_digits(d->name)){
                char path[128],comm[128],cmd[512];usize p=0;
                p=append_text(path,0,sizeof(path),"/proc/");p=append_text(path,p,sizeof(path),d->name);append_text(path,p,sizeof(path),"/comm");
                comm[0]=0;(void)read_small(path,comm,sizeof(comm));
                p=append_text(path,0,sizeof(path),"/proc/");p=append_text(path,p,sizeof(path),d->name);append_text(path,p,sizeof(path),"/cmdline");
                cmd[0]=0;(void)read_small(path,cmd,sizeof(cmd));
                if(eq_ci(trim(comm),"bridge")||contains_ci(cmd,"com.qtrun.QuickTest")){found=1;break;}
            }
            off+=d->reclen;
        }
    }
    sys_close(dfd);return found;
}
static void update_nsg_warning(void){S.nsg_running=detect_nsg_running();}

/* Automatic radio stack reload via consecutive AT+CFUN cycle */
static int at_reload_radio(void){
    char resp[256];
    /* Do not slam CFUN into the tail of an NV write burst. */
    sleep_ms(AT_CFUN_SETTLE_MS);
    (void)at_exec("AT+CFUN=0", resp, sizeof(resp), AT_CFUN_TIMEOUT_MS);
    sleep_ms(150);
    return at_exec("AT+CFUN=1", resp, sizeof(resp), AT_CFUN_TIMEOUT_MS);
}

static void print_usage_examples(void){
    out("USAGE EXAMPLES\n");
    out("  lte 1,3,28,40,41       - Force specific LTE bands\n");
    out("  lte all | lte none     - Reset to all / clear LTE bands\n");
    out("  nr 1,28,41             - Force both SA & NSA to n1,n28,n41\n");
    out("  sa 1,28,41             - Force 5G NR SA bands\n");
    out("  nsa 28,41              - Force 5G NR NSA bands\n");
    out("  wcdma 1,5,8            - Compact WCDMA list (valid: 1,2,4,5,8)\n");
    out("  wcdma all              - Select all five exposed WCDMA bands\n");
    out("  gsm 900,1800           - Force GSM bands (850, 900, 1800, 1900)\n");
    out("  gsm all | gsm none     - Reset / clear GSM bands\n");
    out("  mode both | nsa | sa | lte - Set NR operating mode\n");
    out("  vonr on | off | def    - Enable / disable VoNR (0x1/0x0/0xFF)\n");
    out("  apply                  - Manually reload radio stack (AT+CFUN cycle)\n");
    out("\nTip: Commands can be chained. e.g. \"lte 1,3,28 sa 1,28,41 wcdma 1,8 gsm 900,1800\"\n");
}

static void draw(void){
    out("\033[2J\033[H");
    out("============================================\n");
    out("Shannon Band Menu V5 — Direct AT/NV Backend (4.5.1 parity)\n");
    out("============================================\n");
    print_usage_examples();
    out("--------------------------------------------\n");

    out("LTE: ");
    if(S.lte.valid){
        if(!S.lte.manual_enabled) out("All Bands (Auto / Default)\n");
        else print_mask(S.lte.mask, 256, "B");
    } else out("unavailable\n");

    out("NR-NSA: ");
    if(S.mode_known && (S.mode==2 || S.mode==3)) out("inactive in current NR mode\n");
    else if(S.nsa.valid) print_mask(S.nsa.mask, 512, "n"); else out("unavailable\n");

    out("NR-SA: ");
    if(S.mode_known && (S.mode==1 || S.mode==3)) out("inactive in current NR mode\n");
    else if(S.sa.valid) print_mask(S.sa.mask, 512, "n"); else out("unavailable\n");

    out("WCDMA: ");
    if(S.wcdma.valid){
        if(!S.wcdma.manual_enabled) out("All Bands (Auto / Default)\n");
        else print_mask(S.wcdma.mask, 32, "B");
    } else out("unavailable\n");

    out("GSM: ");
    if(S.gsm.valid){
        if(!S.gsm.manual_enabled) out("All Bands (Auto / Default)\n");
        else print_gsm_mask(S.gsm.mask);
    } else out("unavailable\n");

    out("NR MODE: ");
    if(!S.mode_known) out("unknown\n");
    else if(S.mode==0) out("SA+NSA\n");
    else if(S.mode==1) out("NSA only\n");
    else if(S.mode==2) out("SA only\n");
    else out("Disabled / LTE only\n");

    out("VoNR: ");
    if(S.vonr_state == 1) out("Forced ON (0x01)\n");
    else if(S.vonr_state == 0) out("Forced OFF (0x00)\n");
    else if(S.vonr_state == -1) out("Default / Carrier Policy (0xFF)\n");
    else out("unavailable\n");

    if(S.nsg_running) out("WARNING: NSG is running. May conflict with direct NV writes.\n");
    if(S.status[0]){ out("\nMessage: "); out(S.status); out("\n"); }
    out("============================================\nInput: ");
}

static void help(void){
    out("\nAvailable Commands:\n");
    out("  lte all | lte none | lte 1,3,28,75\n");
    out("  nr all | nr none | nr 1,28,41  (sets both SA and NSA)\n");
    out("  nsa all | nsa none | nsa 28,41\n");
    out("  sa all | sa none | sa 1,28,41\n");
    out("  wcdma all | wcdma none | wcdma 1,2,4,5,8\n");
    out("  gsm all | gsm none | gsm 900,1800 | gsm 850,1900\n");
    out("  mode lte | mode nsa | mode sa | mode both  (SA uses 0x10)\n");
    out("  vonr on | vonr off | vonr default\n");
    out("  apply | refresh | restart\n");
    out("  help | exit\n\n");
    out("Writes are validated first, unchanged families are skipped, and one CFUN cycle is used.\n");
    out("SA/NSA 'none' clears the manual list (active-mode readback becomes Auto/All).\n\n");
}

static int sep(char c){return c==','||c==' '||c=='\t';}
static int puint(const char*s,u32*v){u32 n=0;int d=0;while(*s){if(*s<'0'||*s>'9')return 0;n=n*10u+(u32)(*s-'0');if(n>10000)return 0;s++;d++;}*v=n;return d>0;}
static int plist(char*s,u8*mask,u32 max){char*p=s,*a;u32 x;zero(mask,MASK_BYTES);while(*p){while(*p&&sep(*p))p++;if(!*p)break;a=p;while(*p&&!sep(*p))p++;if(*p)*p++=0;if(!puint(a,&x)||x<1||x>max)return 0;mask_set(mask,x);}return mask_any(mask);}

enum op_type{OP_LTE,OP_NSA,OP_SA,OP_WCDMA,OP_GSM,OP_MODE,OP_VONR};
struct op{enum op_type type;enum band_spec spec;u8 mask[MASK_BYTES];int mode;};
static int wordeq(const char*s,u32 n,const char*w){u32 i=0;while(w[i]&&i<n&&s[i]==w[i])i++;return i==n&&w[i]==0;}
static int command_word(const char*s,u32 n){return wordeq(s,n,"lte")||wordeq(s,n,"nr")||wordeq(s,n,"nsa")||wordeq(s,n,"sa")||wordeq(s,n,"wcdma")||wordeq(s,n,"gsm")||wordeq(s,n,"mode")||wordeq(s,n,"vonr");}

static int fill_band_op(struct op*o,enum op_type type,const char*arg){
    zero(o,sizeof(*o));o->type=type;
    if(eq_ci(arg,"all")){o->spec=SPEC_ALL;return 1;}
    if(eq_ci(arg,"none")){o->spec=SPEC_NONE;return 1;}
    o->spec=SPEC_LIST;
    {char tmp[512];copy_text(tmp,sizeof(tmp),arg);return plist(tmp,o->mask,type==OP_WCDMA?32:(type==OP_GSM?2000:(type==OP_LTE?256:512)));}
}

static int parse_ops(char*l,struct op*ops,int*count){
    u32 pos=0,len=(u32)slen(l);int c=0;
    while(pos<len){
        u32 cs,ce,as,ae,i,j;char cmd[16],arg[512];
        while(pos<len&&(l[pos]==' '||l[pos]=='\t'||l[pos]==';'))pos++;
        if(pos>=len)break;
        cs=pos;while(pos<len&&l[pos]!=' '&&l[pos]!='\t'&&l[pos]!=';')pos++;ce=pos;
        if(!command_word(l+cs,ce-cs))return 0;
        while(pos<len&&(l[pos]==' '||l[pos]=='\t'))pos++;
        if(pos>=len||l[pos]==';')return 0;
        as=pos;ae=len;
        for(i=pos;i<len;i++){
            if(l[i]==';'){ae=i;break;}
            if(l[i]==' '||l[i]=='\t'){
                u32 k=i;while(k<len&&(l[k]==' '||l[k]=='\t'))k++;
                if(k<len&&l[k]!=';'){
                    u32 we=k;while(we<len&&l[we]!=' '&&l[we]!='\t'&&l[we]!=';')we++;
                    if(command_word(l+k,we-k)){ae=i;break;}
                }
            }
        }
        while(ae>as&&(l[ae-1]==' '||l[ae-1]=='\t'))ae--;
        i=0;for(j=cs;j<ce&&i+1<sizeof(cmd);j++)cmd[i++]=l[j];cmd[i]=0;
        i=0;for(j=as;j<ae&&i+1<sizeof(arg);j++)arg[i++]=l[j];arg[i]=0;
        if(eq(cmd,"nr")){
            if(c+2>MAX_OPS)return 0;
            if(!fill_band_op(&ops[c],OP_SA,arg))return 0;
            ops[c+1]=ops[c];ops[c+1].type=OP_NSA;c+=2;
        }else if(eq(cmd,"mode")){
            if(c>=MAX_OPS)return 0;zero(&ops[c],sizeof(ops[c]));ops[c].type=OP_MODE;
            if(eq_ci(arg,"both")||eq_ci(arg,"sa+nsa")||eq_ci(arg,"nsa+sa"))ops[c].mode=0;
            else if(eq_ci(arg,"nsa"))ops[c].mode=1;
            else if(eq_ci(arg,"sa"))ops[c].mode=2;
            else if(eq_ci(arg,"disable")||eq_ci(arg,"off")||eq_ci(arg,"lte"))ops[c].mode=3;
            else return 0;c++;
        }else if(eq(cmd,"vonr")){
            if(c>=MAX_OPS)return 0;zero(&ops[c],sizeof(ops[c]));ops[c].type=OP_VONR;
            if(eq_ci(arg,"on")||eq_ci(arg,"1")||eq_ci(arg,"enable"))ops[c].mode=1;
            else if(eq_ci(arg,"off")||eq_ci(arg,"0")||eq_ci(arg,"disable"))ops[c].mode=0;
            else if(eq_ci(arg,"def")||eq_ci(arg,"default")||eq_ci(arg,"auto"))ops[c].mode=-1;
            else return 0;c++;
        }else{
            enum op_type type;
            if(c>=MAX_OPS)return 0;
            if(eq(cmd,"lte"))type=OP_LTE;
            else if(eq(cmd,"nsa"))type=OP_NSA;
            else if(eq(cmd,"sa"))type=OP_SA;
            else if(eq(cmd,"wcdma"))type=OP_WCDMA;
            else type=OP_GSM;
            if(!fill_band_op(&ops[c],type,arg))return 0;c++;
        }
        pos=ae;
    }
    *count=c;return c>0;
}

static struct family_state *op_family(enum op_type type){
    if(type==OP_LTE)return &S.lte;
    if(type==OP_NSA)return &S.nsa;
    if(type==OP_SA)return &S.sa;
    if(type==OP_WCDMA)return &S.wcdma;
    if(type==OP_GSM)return &S.gsm;
    return NULL;
}

static const u8 *op_supported(enum op_type type){
    if(type==OP_LTE)return S.supported_lte;
    if(type==OP_NSA||type==OP_SA)return S.supported_sa;
    if(type==OP_WCDMA)return S.supported_wcdma;
    if(type==OP_GSM)return S.supported_gsm;
    return NULL;
}

static int op_family_active(enum op_type type){
    if(type==OP_SA && S.mode_known && (S.mode==1||S.mode==3))return 0;
    if(type==OP_NSA && S.mode_known && (S.mode==2||S.mode==3))return 0;
    return 1;
}

static int band_op_valid(const struct op *o){
    const u8 *supported=op_supported(o->type);
    if(!supported)return 0;
    if(o->spec==SPEC_ALL||o->spec==SPEC_NONE)return 1;
    return mask_any(o->mask)&&mask_subset(o->mask,supported);
}

static int band_op_matches(const struct op *o){
    struct family_state *family=op_family(o->type);
    const u8 *supported=op_supported(o->type);
    if(!family||!supported||!family->valid||!op_family_active(o->type))return 0;
    if(o->spec==SPEC_ALL)return mask_equal(family->mask,supported);
    if(o->spec==SPEC_NONE){
        /* NR off is verified through NR.CONFIG.MODE, not the retained lists. */
        if(o->type==OP_SA||o->type==OP_NSA)return 1;
        return !mask_any(family->mask);
    }
    return mask_equal(family->mask,o->mask);
}

static int verify_ops(struct op *ops,int n){
    int i;
    for(i=0;i<n;i++){
        if(ops[i].type==OP_MODE){if(!S.mode_known||S.mode!=ops[i].mode)return 0;}
        else if(ops[i].type==OP_VONR){if(S.vonr_state!=ops[i].mode)return 0;}
        else if(op_family_active(ops[i].type)&&!band_op_matches(&ops[i]))return 0;
    }
    return 1;
}

static int execute_ops(struct op*ops,int n){
    int i;
    int has_changes = 0;
    u32 seen_types = 0;

    /* Validate the whole command before the first NV mutation. */
    for(i=0;i<n;i++){
        u32 type_bit = 1u << (u32)ops[i].type;
        if(seen_types & type_bit){
            setstatus("Rejected duplicate family in one command; no NV items were changed.");
            return 0;
        }
        seen_types |= type_bit;
        if(ops[i].type!=OP_MODE&&ops[i].type!=OP_VONR&&!band_op_valid(&ops[i])){
            setstatus("Rejected unsupported band selection; no NV items were changed.");
            return 0;
        }
    }

    /* Compare against a fresh hardware snapshot and skip identical families. */
    (void)refresh_all_state();

    for(i = 0; i < n; i++){
        if(ops[i].type == OP_MODE && S.mode_known && S.mode == ops[i].mode) continue;
        if(ops[i].type == OP_VONR && S.vonr_state == ops[i].mode) continue;
        if(ops[i].type != OP_MODE && ops[i].type != OP_VONR && band_op_matches(&ops[i])) continue;
        /* NR off leaves the SA/NSA lists untouched; the mode NV does the gating. */
        if((ops[i].type == OP_SA || ops[i].type == OP_NSA) && ops[i].spec == SPEC_NONE) continue;

        if(ops[i].type == OP_LTE){
            if(write_lte_state(ops[i].mask, ops[i].spec) < 0){
                setstatus("Failed to write LTE NV configuration.");
                return 0;
            }
            has_changes = 1;
        } else if(ops[i].type == OP_SA){
            if(write_sa_state(ops[i].mask, ops[i].spec) < 0){
                setstatus("Failed to write NR SA NV configuration.");
                return 0;
            }
            has_changes = 1;
        } else if(ops[i].type == OP_NSA){
            if(write_nsa_state(ops[i].mask, ops[i].spec) < 0){
                setstatus("Failed to write NR NSA NV configuration.");
                return 0;
            }
            has_changes = 1;
        } else if(ops[i].type == OP_MODE){
            if(write_nr_mode(ops[i].mode) < 0){
                setstatus("Failed to write NR mode NV configuration.");
                return 0;
            }
            has_changes = 1;
        } else if(ops[i].type == OP_WCDMA){
            if(write_wcdma_state(ops[i].mask, ops[i].spec) < 0){
                setstatus("Failed to write WCDMA NV configuration.");
                return 0;
            }
            has_changes = 1;
        } else if(ops[i].type == OP_GSM){
            if(write_gsm_state(ops[i].mask, ops[i].spec) < 0){
                setstatus("Failed to write GSM NV configuration.");
                return 0;
            }
            has_changes = 1;
        } else if(ops[i].type == OP_VONR){
            if(write_vonr_state(ops[i].mode) < 0){
                setstatus("Failed to write VoNR NV configuration.");
                return 0;
            }
            has_changes = 1;
        }
    }

    if(has_changes){
        int mode_in_batch = 0;
        for(i = 0; i < n; i++) if(ops[i].type == OP_MODE) mode_in_batch = 1;
        /* A menu-driven mode change performs its own radio reload, and that
         * reload adopts the band NV writes above (verified on device). Only
         * issue our own CFUN when no mode change accompanies them. */
        if(mode_in_batch){
            (void)refresh_all_state();
            if(verify_ops(ops,n)) setstatus("Applied via modem menu and verified by direct AT/NV readback.");
            else setstatus("Applied via modem menu, but direct AT/NV readback differs.");
        } else if(at_reload_radio() == 0){
            (void)refresh_all_state();
            if(verify_ops(ops,n)) setstatus("Applied, radio reloaded, and direct AT/NV readback verified.");
            else setstatus("Applied and radio reloaded, but direct AT/NV readback differs.");
        } else {
            (void)refresh_all_state();
            setstatus("Configuration written to NV. Auto-reload failed; toggle Airplane Mode.");
        }
    } else {
        setstatus("No modem changes needed; requested state already matches AT/NV readback.");
    }
    return 1;
}

static long readline(char*b,u64 cap){
    u64 used=0;if(cap<2)return-1;
    for(;;){
        char c;long n=sys_read(0,&c,1);
        if(n<0){b[used]=0;return used?(long)used:n;}
        if(n==0){b[used]=0;return(long)used;}
        if(c=='\r'||c=='\n'){out("\n");b[used]=0;return(long)used;}
        if(c==8||c==127){if(used){used--;out("\b \b");}continue;}
        if(c>=' '&&c!=127&&used+1<cap){b[used++]=c;(void)sys_write(1,&c,1);}
    }
}

static int handle(char*line){
    struct op ops[MAX_OPS];int n;char*l=trim(line);
    if(!*l)return 1;
    if(eq_ci(l,"exit")||eq_ci(l,"quit"))return-1;
    if(eq_ci(l,"help")){help();return 1;}
    if(eq_ci(l,"apply")||eq_ci(l,"reload")){
        setstatus("Reloading modem radio stack...");
        if(at_reload_radio() == 0){
            refresh_all_state();
            setstatus("Modem radio stack reloaded successfully (AT+CFUN cycle).");
        } else {
            setstatus("Failed to reload radio stack via AT+CFUN.");
        }
        return 1;
    }
    if(eq_ci(l,"refresh")){
        update_nsg_warning();
        refresh_all_state();
        setstatus("State refreshed from Shannon NV items.");
        return 1;
    }
    if(eq_ci(l,"restart")){
        at_close();
        sleep_ms(100);
        update_nsg_warning();
        init_hardware_bands();
        refresh_all_state();
        setstatus("Router connection reopened and state refreshed.");
        return 1;
    }
    if(!parse_ops(l,ops,&n)){
        setstatus("Invalid command or syntax. Type help for usage.");
        return 1;
    }
    (void)execute_ops(ops,n);
    return 1;
}

static int run(void){
    char line[768];
    zero(&S, sizeof(S));
    setstatus("Initializing Shannon direct NV backend...");
    update_nsg_warning();

    if(at_open() < 0){
        err("Cannot open /dev/umts_router. Ensure you run this binary as root.\n");
        return 1;
    }

    init_hardware_bands();
    refresh_all_state();
    setstatus("Ready. Direct NV backend active.");

    for(;;){
        draw();
        if(readline(line, sizeof(line)) < 0) break;
        if(handle(line) < 0) break;
    }

    out("\nExiting Shannon Band Menu V5...\n");
    at_close();
    return 0;
}

__attribute__((used))void c_start(long*stack){(void)stack;sys_exit(run());}
__asm__(".global _start\n.type _start,%function\n_start:\nmov x0,sp\nbl c_start\nmov x0,#127\nmov x8,#93\nsvc #0\n");
