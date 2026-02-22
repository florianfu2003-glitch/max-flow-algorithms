package ega.testbed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Command-line entry point for running the max-flow testbed.
 *
 * <p>This runner focuses on:
 * <ul>
 *   <li>selecting a test mode / batch configuration,</li>
 *   <li>initializing the RNG (seeded or auto),</li>
 *   <li>delegating all correctness checks and reporting to {@link TestEnvironment}.</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 *   # Equivalent to the default behavior (5 instances, n=12, cap=50, seed=123)
 *   java ega.testbed.Main --mode=batch --instances=5 --n=12 --cap=50 --seed=123
 *
 *   # Small demo run (same config as the GUI quick test)
 *   java ega.testbed.Main --mode=small
 *
 *   # Larger run (same config as the GUI full test)
 *   java ega.testbed.Main --mode=big --seed=2025
 * </pre>
 *
 * <p>Options:
 * <ul>
 *   <li>{@code --mode=<small|batch|big>} (default: batch)</li>
 *   <li>{@code --instances=<int>}        (batch mode only; default: 5)</li>
 *   <li>{@code --n=<int>}                (batch mode only; default: 12)</li>
 *   <li>{@code --cap=<int>}              (batch mode only; default: 50)</li>
 *   <li>{@code --seed=<long>}            (default: 123; if omitted, uses {@code System.nanoTime()})</li>
 *   <li>{@code --help}                   (print help)</li>
 * </ul>
 */
public class Main {

    enum Mode { SMALL, BATCH, BIG }

    static class Options {
        Mode mode = Mode.BATCH;
        int instances = 5;
        int n = 12;
        int cap = 50;
        Long seed = 123L;   // If set to null, a seed is derived from System.nanoTime().
        boolean help = false;
    }

    public static void main(String[] args) {
        Options opt = parseArgs(args);
        if (opt.help) {
            printUsage();
            return;
        }

        printHeader(opt);

        List<TestEnvironment.BatchConfig> batches = new ArrayList<>();

        Random rng;
        if (opt.seed != null) {
            rng = new Random(opt.seed);
            println("Seed: " + opt.seed);
        } else {
            long s = System.nanoTime();
            rng = new Random(s);
            println("Seed (auto): " + s);
        }
        println("");

        switch (opt.mode) {
            case SMALL:
                batches.add(new TestEnvironment.BatchConfig(3, 20, 50));
                batches.add(new TestEnvironment.BatchConfig(2, 40, 50));
                break;
            case BIG:
                batches.add(new TestEnvironment.BatchConfig(20, 50, 100));
                batches.add(new TestEnvironment.BatchConfig(15, 80, 100));
                batches.add(new TestEnvironment.BatchConfig(10, 120, 100));
                break;
            case BATCH:
            default:
                batches.add(new TestEnvironment.BatchConfig(opt.instances, opt.n, opt.cap));
                break;
        }

        println("=== EGA Max-Flow Test Report (" + opt.mode + ") ===");

        TestEnvironment.runBatches(
                batches,
                rng,
                Main::println
        );

        println("=== Test run finished. ===");
    }

    /* ============================= Argument parsing & printing ============================= */

    private static Options parseArgs(String[] args) {
        Options o = new Options();

        if (args == null) return o;

        for (String a : args) {
            if (a == null) continue;

            String s = a.trim();
            if (s.isEmpty()) continue;

            if (s.equals("--help") || s.equals("-h")) {
                o.help = true;
                continue;
            }

            if (s.startsWith("--mode=")) {
                String v = s.substring("--mode=".length()).trim().toLowerCase(Locale.ROOT);
                switch (v) {
                    case "small": o.mode = Mode.SMALL; break;
                    case "big":   o.mode = Mode.BIG;   break;
                    case "batch": o.mode = Mode.BATCH; break;
                    default:
                        System.err.println("Unknown mode: " + v + " (expected: small|batch|big)");
                        o.help = true;
                }
                continue;
            }

            if (s.startsWith("--instances=")) {
                o.instances = parseIntOrWarn(s, "--instances=");
                continue;
            }

            if (s.startsWith("--n=")) {
                o.n = parseIntOrWarn(s, "--n=");
                continue;
            }

            if (s.startsWith("--cap=")) {
                o.cap = parseIntOrWarn(s, "--cap=");
                continue;
            }

            if (s.startsWith("--seed=")) {
                try {
                    o.seed = Long.parseLong(s.substring("--seed=".length()).trim());
                } catch (NumberFormatException e) {
                    System.err.println("Cannot parse --seed, using default 123.");
                    o.seed = 123L;
                }
                continue;
            }

            System.err.println("Unknown argument: " + s);
            o.help = true;
        }

        return o;
    }

    private static int parseIntOrWarn(String src, String key) {
        try {
            return Integer.parseInt(src.substring(key.length()).trim());
        } catch (NumberFormatException e) {
            System.err.println("Cannot parse integer argument: " + src);
            return 0;
        }
    }

    private static void printUsage() {
        String u =
                "Usage:\n" +
                        "  java ega.testbed.Main [options]\n\n" +
                        "Options:\n" +
                        "  --mode=<small|batch|big>   test mode (default: batch)\n" +
                        "  --instances=<int>          batch mode: number of instances (default: 5)\n" +
                        "  --n=<int>                  batch mode: number of nodes (default: 12)\n" +
                        "  --cap=<int>                batch mode: capacity upper bound (default: 50)\n" +
                        "  --seed=<long>              RNG seed (default: 123; if omitted: System.nanoTime())\n" +
                        "  --help                     print this help\n\n" +
                        "Examples:\n" +
                        "  java ega.testbed.Main --mode=batch --instances=5 --n=12 --cap=50 --seed=123\n" +
                        "  java ega.testbed.Main --mode=small\n" +
                        "  java ega.testbed.Main --mode=big --seed=2025\n";
        System.out.print(u);
    }

    private static void printHeader(Options opt) {
        println("Student: Bo Fu");
        println("Matrikelnummer: 3820106");
        println("Course: Effiziente Graphenalgorithmen (EGA) 2025/26");
        println("Date:  " + LocalDate.now());
        println("Time:  " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        println("Java:  " + System.getProperty("java.version")
                + "  |  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        println("Mode:  " + opt.mode);

        if (opt.mode == Mode.BATCH) {
            println(String.format(Locale.ROOT, "Batch params: instances=%d, n=%d, cap=%d",
                    opt.instances, opt.n, opt.cap));
        }

        println("==============================================\n");
    }

    private static void println(String s) {
        System.out.println(s);
    }
}
