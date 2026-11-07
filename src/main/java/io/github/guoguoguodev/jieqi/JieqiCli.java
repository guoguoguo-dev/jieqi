package io.github.guoguoguodev.jieqi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class JieqiCli {

    static final String VERSION = "0.1.0";
    static final int TERM_COUNT = 24;
    /** RFC 5545 §3.1：一行（含换行符前的所有八位组）不得超过 75 个八位组。 */
    static final int ICS_MAX_OCTETS = 75;

    record Term(int year, int index, String name, MonthDay md,
                String phenology, String custom) {
        LocalDate date() {
            return md.atYear(year);
        }

        String phenologyText() {
            return phenology.replace(";", "、");
        }
    }

    /**
     * 在 main 中加载，而不是写成静态初始化表达式：
     * 静态初始化失败会抛 ExceptionInInitializerError，那段友好的提示就再也显示不出来。
     */
    static List<Term> TERMS = List.of();

    public static void main(String[] args) {
        int code;
        try {
            TERMS = load();
            code = run(args);
        } catch (UsageException e) {
            System.err.println(e.getMessage());
            System.err.println("运行 jieqi help 查看用法");
            code = 2;
        } catch (IllegalStateException e) {
            System.err.println("数据错误: " + e.getMessage());
            code = 1;
        } catch (UncheckedIOException e) {
            System.err.println("读取数据失败: " + e.getCause().getMessage());
            code = 1;
        } catch (IOException e) {
            System.err.println("文件读写失败: " + e.getMessage());
            code = 1;
        }
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(String[] args) throws IOException {
        if (args.length == 0) {
            help();
            return 0;
        }

        switch (args[0]) {
            case "list" -> {
                require(args, 2, "用法: jieqi list <年份>");
                list(parseYear(args[1]));
            }
            case "next" -> next();
            case "on" -> {
                require(args, 2, "用法: jieqi on <yyyy-MM-dd>");
                on(parseDate(args[1]));
            }
            case "export" -> export(args);
            case "help", "-h", "--help" -> help();
            case "version", "-V", "--version" -> System.out.println("jieqi " + VERSION);
            default -> throw new UsageException("未知命令: " + args[0]);
        }
        return 0;
    }

    static int parseYear(String raw) {
        try {
            int year = Integer.parseInt(raw.trim());
            if (year < 1900 || year > 2100) {
                throw new UsageException("年份需在 1900-2100 之间: " + raw);
            }
            return year;
        } catch (NumberFormatException e) {
            throw new UsageException("年份必须是整数: " + raw);
        }
    }

    static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new UsageException("日期格式应为 yyyy-MM-dd: " + raw);
        }
    }

    static List<Term> load() {
        try (InputStream in = JieqiCli.class.getResourceAsStream("/terms.csv")) {
            if (in == null) {
                throw new IllegalStateException("缺少数据文件 terms.csv");
            }
            try (var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<Term> terms = new ArrayList<>();
                String line;
                int lineNo = 0;
                while ((line = br.readLine()) != null) {
                    lineNo++;
                    String row = line.trim();
                    if (row.isEmpty() || row.startsWith("#")) {
                        continue;
                    }
                    String[] f = row.split("\\|", -1);
                    if (f.length != 6) {
                        throw new IllegalStateException("第 " + lineNo
                                + " 行应为 6 个以 | 分隔的字段，实际为 " + f.length + " 个");
                    }
                    try {
                        int year = Integer.parseInt(f[0].trim());
                        int index = Integer.parseInt(f[1].trim());
                        if (index < 1 || index > TERM_COUNT) {
                            throw new IllegalArgumentException(
                                    "序号必须在 1-" + TERM_COUNT + " 之间: " + index);
                        }
                        String name = f[2].trim();
                        if (name.isEmpty()) {
                            throw new IllegalArgumentException("节气名不能为空");
                        }
                        MonthDay md;
                        try {
                            md = MonthDay.parse("--" + f[3].trim());
                        } catch (DateTimeParseException e) {
                            throw new IllegalArgumentException(
                                    "日期列应为零填充的 MM-dd（例如 01-05），实际为: " + f[3].trim());
                        }
                        terms.add(new Term(year, index, name, md, f[4].trim(), f[5].trim()));
                    } catch (RuntimeException e) {
                        throw new IllegalStateException(
                                "第 " + lineNo + " 行无法解析: " + e.getMessage(), e);
                    }
                }
                return validate(terms);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Term> validate(List<Term> terms) {
        var sorted = terms.stream()
                .sorted(Comparator.comparingInt(Term::year)
                        .thenComparingInt(Term::index))
                .toList();

        Map<Integer, List<Term>> byYear = new LinkedHashMap<>();
        for (Term t : sorted) {
            byYear.computeIfAbsent(t.year(), y -> new ArrayList<>()).add(t);
        }

        byYear.forEach((year, list) -> {
            Set<Integer> seen = new HashSet<>();
            LocalDate prev = null;
            for (Term t : list) {
                if (!seen.add(t.index())) {
                    System.err.println("警告: " + year + " 年序号 " + t.index()
                            + " 重复（" + t.name() + "）");
                }
                if (prev != null && !t.date().isAfter(prev)) {
                    System.err.println("警告: " + year + " 年 " + t.name()
                            + " 的日期未按顺序递增");
                }
                prev = t.date();
            }
            if (list.size() < TERM_COUNT) {
                System.err.println("警告: " + year + " 年只有 " + list.size()
                        + " 个节气，应为 " + TERM_COUNT + " 个");
            }
        });

        return List.copyOf(sorted);
    }

    static List<Term> byYear(int year) {
        return TERMS.stream()
                .filter(t -> t.year() == year)
                .sorted(Comparator.comparingInt(Term::index))
                .toList();
    }

    static void list(int year) {
        var terms = byYear(year);
        if (terms.isEmpty()) {
            System.out.println("暂无 " + year + " 年数据，请扩充 terms.csv");
            return;
        }
        System.out.println("二十四节气 · " + year + "（" + terms.size() + " 个）");
        System.out.println("----------------------------------------");
        terms.forEach(JieqiCli::printTerm);
    }

    static void next() {
        LocalDate today = LocalDate.now();
        var next = TERMS.stream()
                .filter(t -> !t.date().isBefore(today))
                .min(Comparator.comparing(Term::date));

        if (next.isEmpty()) {
            System.out.println("当前日期之后暂无数据，请扩充 terms.csv");
            return;
        }

        long days = ChronoUnit.DAYS.between(today, next.get().date());
        if (days == 0) {
            System.out.println("今日节气");
        } else {
            System.out.println("距离下一个节气还有 " + days + " 天");
        }
        printTerm(next.get());
    }

    static void on(LocalDate date) {
        var exact = TERMS.stream().filter(t -> t.date().equals(date)).findFirst();
        if (exact.isPresent()) {
            // 查的是任意日期，只有正好是今天才能叫“今日节气”。
            if (date.equals(LocalDate.now())) {
                System.out.println("今日节气");
            } else {
                System.out.println(date + " 是节气日");
            }
            printTerm(exact.get());
            return;
        }

        System.out.println(date + " 不是节气日");
        TERMS.stream()
                .filter(t -> t.date().isBefore(date))
                .max(Comparator.comparing(Term::date))
                .ifPresent(t -> System.out.println("上一个：" + t.date() + " " + t.name()));
        TERMS.stream()
                .filter(t -> t.date().isAfter(date))
                .min(Comparator.comparing(Term::date))
                .ifPresent(t -> System.out.println("下一个：" + t.date() + " " + t.name()));
    }

    static void printTerm(Term t) {
        String week = t.date().getDayOfWeek()
                .getDisplayName(TextStyle.SHORT, Locale.CHINA);
        // 不用 %-4s 按字符数补齐：中文显示宽度是 2，按字符补齐会错位。
        System.out.printf("%s %s  %2d. %s  三候：%s  习俗：%s%n",
                t.date(), week, t.index(), t.name(),
                t.phenologyText(), t.custom());
    }

    static void export(String[] args) throws IOException {
        if (args.length < 3) {
            throw new UsageException("用法: jieqi export ics|md <年份> [输出文件]");
        }

        String type = args[1];
        int year = parseYear(args[2]);
        var terms = byYear(year);

        if (terms.isEmpty()) {
            System.out.println("暂无 " + year + " 年数据，请扩充 terms.csv");
            return;
        }

        String content = switch (type) {
            case "ics" -> toIcs(terms);
            case "md" -> toMarkdown(terms);
            default -> throw new UsageException("未知格式: " + type + "（可选 ics 或 md）");
        };

        if (args.length >= 4) {
            Files.writeString(Path.of(args[3]), content, StandardCharsets.UTF_8);
            System.out.println("已写入 " + args[3]);
        } else {
            System.out.print(content);
        }
    }

    static String toIcs(List<Term> terms) {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());

        var sb = new StringBuilder();
        fold(sb, "BEGIN:VCALENDAR");
        fold(sb, "VERSION:2.0");
        fold(sb, "PRODID:-//jieqi//二十四节气日历//CN");
        fold(sb, "CALSCALE:GREGORIAN");
        fold(sb, "METHOD:PUBLISH");
        fold(sb, "X-WR-CALNAME:二十四节气");
        fold(sb, "X-WR-CALDESC:由 jieqi 生成的二十四节气日历");

        for (Term t : terms) {
            LocalDate d = t.date();
            fold(sb, "BEGIN:VEVENT");
            fold(sb, "UID:" + d + "-" + t.index() + "@jieqi");
            fold(sb, "DTSTAMP:" + stamp);
            fold(sb, "DTSTART;VALUE=DATE:" + d.format(DateTimeFormatter.BASIC_ISO_DATE));
            fold(sb, "DTEND;VALUE=DATE:"
                    + d.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE));
            fold(sb, "SUMMARY:" + icsEscape(t.name()));
            fold(sb, "DESCRIPTION:" + icsEscape(
                    "三候：" + t.phenologyText() + "\n习俗：" + t.custom()));
            fold(sb, "CATEGORIES:二十四节气");
            fold(sb, "TRANSP:TRANSPARENT");
            fold(sb, "END:VEVENT");
        }
        fold(sb, "END:VCALENDAR");
        return sb.toString();
    }

    /**
     * 按 RFC 5545 §3.1 折行：首行不超过 75 个八位组，续行以单个空格开头，
     * 且连同该空格在内同样不超过 75 个八位组；不会把一个 UTF-8 字符劈成两半。
     */
    static void fold(StringBuilder sb, String line) {
        int limit = ICS_MAX_OCTETS;
        int start = 0;
        boolean first = true;
        while (true) {
            int i = start;
            int octets = 0;
            while (i < line.length()) {
                int cp = line.codePointAt(i);
                int len = utf8Length(cp);
                if (octets + len > limit) {
                    break;
                }
                octets += len;
                i += Character.charCount(cp);
            }
            if (!first) {
                sb.append(' ');
            }
            sb.append(line, start, i).append("\r\n");
            if (i >= line.length()) {
                return;
            }
            start = i;
            first = false;
            limit = ICS_MAX_OCTETS - 1;
        }
    }

    static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        if (codePoint < 0x10000) {
            return 3;
        }
        return 4;
    }

    static String toMarkdown(List<Term> terms) {
        var sb = new StringBuilder();
        sb.append("| 日期 | 节气 | 三候 | 习俗 |\n")
          .append("|---|---|---|---|\n");
        for (Term t : terms) {
            sb.append("| %s | %s | %s | %s |\n".formatted(
                    t.date(),
                    mdCell(t.name()),
                    mdCell(t.phenologyText()),
                    mdCell(t.custom())));
        }
        return sb.toString();
    }

    static String mdCell(String s) {
        return s.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    static String icsEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    static void require(String[] args, int n, String message) {
        if (args.length < n) {
            throw new UsageException(message);
        }
    }

    static void help() {
        System.out.println("""
                节气历 jieqi - 二十四节气 CLI

                用法:
                  jieqi list <年份>
                  jieqi next
                  jieqi on <yyyy-MM-dd>
                  jieqi export ics <年份> [输出文件]
                  jieqi export md <年份> [输出文件]
                  jieqi help
                  jieqi version

                说明:
                  数据来自 resources/terms.csv，欢迎扩充年份与物候内容。
                  退出码: 0 成功；1 数据或文件错误；2 参数用法错误。
                """);
    }

    static final class UsageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }
}
