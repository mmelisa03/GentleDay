import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*; 

public class GentleDay {
    private static final String FILE_NAME = "journal.csv";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static void main(String[] args) {
        ensureFile();

        //Quick flags: --today, --weekly, --help
        if(args.length > 0) {
            switch (args[0]) {
             case "--today" -> reviewToday();
             case "--weekly" -> weeklySummary();
             case "--help" -> {
                println("Usage: java GentleDay [--today|--weekly|--help]");
                return;
             }   
             default -> println("Unknown option. Try --help");
            }
            return;
        }
        try (Scanner sc = new Scanner(System.in)) {
            println("\nChoose an option");
            println(" 1) New entry (intention + grounding timer)");
            println(" 2) Review last N entries");
            println(" 3) Today's entries");
            println(" 4) Weekly summary");
            println(" 5) Exit");
            print("> ");
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1" -> newEntry(sc);
                case "2" -> review(sc);
                case "3" -> reviewToday();
                case "4" -> weeklySummary();
                case "5" -> {
                    println("\nTake good care today. ✨");
                    return; 
                }
                default -> println("Please choose 1-5.");
            }
         }
    }
    


private static void newEntry(Scanner sc) {
    println("\n🕯️ New entry");
    print("Intention for today (one line): ");
    String intention = sc.nextLine().trim();

    print("Optional: mood 1-5 (press Enter to skip): ");
    String moodRaw = sc.nextLine().trim();
    Integer mood = parseMoodOrNull(moodRaw);

    print("One thing you're grateful for (optional, Enter to skip)");
    String gratitude = sc.nextLine().trim();

    //Optimal micro-timer
    print("Do a 3-breath micro-timer before the 60s timer? (Y/N): ");
    String micro = sc.nextLine().trim().toLowerCase(Locale.ROOT);
    if (micro.equals("y") || micro.equals("yes")) {
        microBreaths(3);
    }

    println("\n💨 60-second grounding timer (press Enter to start). . . ");
    sc.nextLine();
    countdown(60);

    String ts = LocalDateTime.now().format(TS);
    writeCsv(ts, intention, mood, gratitude);
    println("\n✅ Saved to " + FILE_NAME);

}

private static void review(Scanner sc) {
    print("\nHow many recent entries to show? ");
    String nRaw = sc.nextLine().trim();
    int n = 5;
    try { n = Math.max(1, Integer.parseInt(nRaw)); } catch (NumberFormatException ignored) {}

    List<String[]> rows = readRows();
    if(rows.isEmpty()) {
        println("\n(No entries yet)");
        return; 
    }

    int start = Math.max(0, rows.size() - n);
    println("\n- Last " + Math.min(n, rows.size()) + " entr" + (rows.size()==1?"y":"ies") + " -");
    for (int i = rows.size() -1; i>= start; i--) {
        printRow(rows.get(i));
    }
}

private static void reviewToday() {
    List<String[]> rows = readRows();
    if (rows.isEmpty()) { println("\n(No entries yet.)"); return; }

    LocalDate today = LocalDate.now();
    List<String[]> todays = new ArrayList<>();
    for(String[] r : rows) {
        LocalDateTime t = parseTs(r[0]);
        if( t != null && t.toLocalDate().equals(today)) {
            todays.add(r);
        }
    }

    if (todays.isEmpty()) {
        println("\n(No entries for today yet.)");
        return; 
    }

    println("\n- Today (" + today + ") -");
    for ( int i = todays.size()-1; i>= 0; i--) {
        printRow(todays.get(i));
    }
}

private static void weeklySummary() {
    List<String[]> rows = readRows();
    if(rows.isEmpty()) { println("\n(No entries yet.)"); return; }

    LocalDate today = LocalDate.now();
    LocalDate start = today.minusDays(6);
    int count = 0;
    int moodSum = 0;
    int moodCount = 0;

    // For streak: Count consecutive days up to today that have >=1 entry
    Set<LocalDate> daysWithEntries = new HashSet<>();

    for (String[] r : rows) {
        LocalDateTime ts = parseTs(r[0]);
        if(ts == null) continue; 
        LocalDate d = ts.toLocalDate();

        if(!d.isBefore(start) && !d.isAfter(today)) {
            count ++;
            daysWithEntries.add(d);
            Integer mood = parseMoodOrNull(r[2]);
            if(mood != null) { moodSum += mood; moodCount++; }
        }
    }

    int streak = 0;
    for(LocalDate d = today; daysWithEntries.contains(d); d = d.minusDays(1)) {
        streak++;
    }

    println("\n- Weekly Summary (" + start + " to " + today + ") -");
    println("Entries: " + count);
    if(moodCount > 0) { 
        double avg = ((double) moodSum) / moodCount;
        println(String.format(Locale.US, "Average mood: %.2f (from %d moods)", avg, moodCount));
    } else {
        println("Average mood: - (no moods logged)");
    }
    println("Current daily streak (ending today): " + (streak > 0 ? streak + " day(s)" : "-"));
}

// CSV helpers

private static void ensureFile() {
    if (!Files.exists(Path.of(FILE_NAME))) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(FILE_NAME, true))) {
            pw.println("timestamp, intention, mood, gratitude");
        } catch (IOException e) {
            err("Could not create journal.csv: " + e.getMessage());
        }
    }
}
private static String escape(String s) {
    if (s == null) return "";
    boolean needQuotes = s.contains(",") || s.contains("\"") || s.contains("\n");
    String body = s.replace("\"", "\"\"");  // escape quotes by doubling them
    return needQuotes ? ("\"" + body + "\"") : body;
}


private static void writeCsv(String ts, String intention, Integer mood, String gratitude) {
    try (PrintWriter pw = new PrintWriter(new FileWriter(FILE_NAME, true))) {
        pw.println(escape(ts) + "," + escape(intention) + "," +
        (mood == null ? "" : mood) + "," + escape(gratitude));
    } catch (IOException e) {
        err("Write failed: " + e.getMessage());
    }
}

private static List<String[]> readRows() {
    List<String> lines;
    try {
        lines = Files.readAllLines(Path.of(FILE_NAME));
    } catch (IOException e) {
        err("Read failed: " + e.getMessage());
        return List.of();
    }
    if(lines.size() <=1) return List.of();

    List<String[]> rows = new ArrayList<>();
    for ( int i = 1; i < lines.size(); i++) {
        rows.add(parseCsvLine(lines.get(i)));
    }
    return rows; 
}

private static String[] parseCsvLine(String line) {
    // minimal CSV parser for 4 columns
    List<String> cols = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (inQuotes) {
            if (c == '"') {
                if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"'); // escaped quote
                    i++;
                } else {
                    inQuotes = false; // closing quote
                }
            } else {
                cur.append(c);
            }
        } else {
            if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                cols.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
    }

    cols.add(cur.toString());
    while (cols.size() < 4) cols.add("");
    return cols.toArray(new String[0]);
}


    // UI Helpers

        private static void countdown(int seconds) {
        long end = System.currentTimeMillis() + seconds * 1000L;
        int last = seconds + 1; // force initial print
        while (true) {
            long left = Math.max(0, (end - System.currentTimeMillis()) / 1000);
            if ((int) left != last) {
                last = (int) left;
                print("\r⏳ " + left + "s remaining...");
            }
            if (left == 0) break;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        print("\rDone.                                              \n");
    }

    private static void microBreaths(int breaths) {
        println("\n🌬  Micro-timer: " + breaths + " breaths");
        for (int i = 1; i <= breaths; i++) {
            breathPhase("Inhale", 4);
            breathPhase("Hold  ", 2);
            breathPhase("Exhale", 6);
        }
        println("✓ Micro-timer complete.");
    }

    private static void breathPhase(String label, int seconds) {
        for (int s = seconds; s >= 1; s--) {
            print("\r" + label + " " + s + "… ");
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        print("\r" + label + " done.     \n");
    }

    private static LocalDateTime parseTs(String ts) {
        try { return LocalDateTime.parse(ts, TS); } catch (Exception e) { return null; }
    }

    private static Integer parseMoodOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int m = Integer.parseInt(raw.trim());
            return (m >= 1 && m <= 5) ? m : null;
        } catch (NumberFormatException e) { return null; }
    }

    private static void printRow(String[] r) {
        String ts = r[0];
        String intention = r[1];
        String mood = (r[2] == null || r[2].isBlank()) ? "—" : r[2];
        String gratitude = (r[3] == null || r[3].isBlank()) ? "—" : r[3];
        println("• " + ts);
        println("   intention: " + intention);
        println("   mood: " + mood + "   gratitude: " + gratitude);
    }

    private static void println(String s) { System.out.println(s); }
    private static void print(String s)   { System.out.print(s); }
    private static void err(String s)     { System.err.println(s); }
}

/*
   Run Instructions:
   cd ~/Desktop/GentleDay
   javac GentleDay.java
   java GentleDay
*/


