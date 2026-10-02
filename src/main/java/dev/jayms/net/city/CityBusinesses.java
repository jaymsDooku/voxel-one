package dev.jayms.net.city;

import java.io.*;
import java.util.*;

/** Operating accounts for physical workplaces. Money movement stays in CityEconomy. */
public final class CityBusinesses {
    public record Totals(
            double revenue,
            double wages,
            double supplies,
            double rent,
            double workHours,
            long produced,
            long sold,
            long received,
            long missedWages,
            long missedRent) {
        public double expenses() {
            return wages + supplies + rent;
        }

        public double profit() {
            return revenue - expenses();
        }

        public static Totals empty() {
            return new Totals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public record Day(long day, Totals totals) {}

    public record Record(
            int building,
            int company,
            long startedDay,
            double progress,
            Totals total,
            Day today,
            List<Day> history) {
        public Record {
            history = List.copyOf(history);
        }
    }

    private static final class Counters {
        double revenue, wages, supplies, rent, hours;
        long produced, sold, received, missedWages, missedRent;

        Counters() {}

        Counters(Totals t) {
            revenue = t.revenue;
            wages = t.wages;
            supplies = t.supplies;
            rent = t.rent;
            hours = t.workHours;
            produced = t.produced;
            sold = t.sold;
            received = t.received;
            missedWages = t.missedWages;
            missedRent = t.missedRent;
        }

        Totals snapshot() {
            return new Totals(
                    revenue,
                    wages,
                    supplies,
                    rent,
                    hours,
                    produced,
                    sold,
                    received,
                    missedWages,
                    missedRent);
        }
    }

    private static final class Account {
        final int building, company;
        final long started;
        long day;
        double progress;
        Counters total = new Counters(), today = new Counters();
        final ArrayDeque<Day> history = new ArrayDeque<>();

        Account(int building, int company, long day) {
            this.building = building;
            this.company = company;
            started = day;
            this.day = day;
        }

        Account(Record r) {
            building = r.building;
            company = r.company;
            started = r.startedDay;
            day = r.today.day;
            progress = r.progress;
            total = new Counters(r.total);
            today = new Counters(r.today.totals);
            history.addAll(r.history);
        }

        Record snapshot() {
            return new Record(
                    building,
                    company,
                    started,
                    progress,
                    total.snapshot(),
                    new Day(day, today.snapshot()),
                    List.copyOf(history));
        }
    }

    private final Map<Integer, Account> accounts = new LinkedHashMap<>();
    private long day = 1;

    public CityBusinesses(List<Record> saved) {
        for (var r : saved) accounts.put(r.building, new Account(r));
    }

    public void beginDay(long currentDay) {
        day = currentDay;
        for (var a : accounts.values())
            if (a.day != day) {
                a.history.addLast(new Day(a.day, a.today.snapshot()));
                while (a.history.size() > 7) a.history.removeFirst();
                a.today = new Counters();
                a.day = day;
            }
    }

    public static boolean shopShift(double hour, int staffIndex) {
        return staffIndex % 2 == 0 ? hour >= 6 && hour < 14 : hour >= 14 && hour < 22;
    }

    public void open(int building, int company) {
        accounts.computeIfAbsent(building, b -> new Account(b, company, day));
    }

    private void update(int building, java.util.function.Consumer<Counters> action) {
        var a = accounts.get(building);
        if (a != null) {
            action.accept(a.total);
            action.accept(a.today);
        }
    }

    public void wage(int building, double amount, double hours) {
        update(
                building,
                c -> {
                    c.wages += amount;
                    c.hours += hours;
                });
    }

    public void missedWage(int building) {
        update(building, c -> c.missedWages++);
    }

    public void rent(int building, double amount) {
        update(building, c -> c.rent += amount);
    }

    public void missedRent(int building) {
        update(building, c -> c.missedRent++);
    }

    public void sale(int building, int units, double amount) {
        update(
                building,
                c -> {
                    c.sold += units;
                    c.revenue += amount;
                });
    }

    public void delivery(int building, int units, double amount) {
        update(
                building,
                c -> {
                    c.received += units;
                    c.supplies += amount;
                });
    }

    public int produce(int building, double hours, int available) {
        var a = accounts.get(building);
        if (a == null || available <= 0) return 0;
        a.progress += hours * 4; // Four supplies per paid worker-hour, independent of game speed.
        int units = Math.min(available, (int) a.progress);
        a.progress -= units;
        if (a.progress >= 1)
            a.progress = 0; // Surplus work cannot manufacture beyond storage capacity.
        update(building, c -> c.produced += units);
        return units;
    }

    public List<Record> records() {
        return accounts.values().stream().map(Account::snapshot).toList();
    }

    public static void write(DataOutput out, List<Record> records) throws IOException {
        out.writeInt(records.size());
        for (var r : records) {
            out.writeInt(r.building);
            out.writeInt(r.company);
            out.writeLong(r.startedDay);
            out.writeDouble(r.progress);
            writeTotals(out, r.total);
            writeDay(out, r.today);
            out.writeInt(r.history.size());
            for (var d : r.history) writeDay(out, d);
        }
    }

    private static void writeDay(DataOutput out, Day d) throws IOException {
        out.writeLong(d.day);
        writeTotals(out, d.totals);
    }

    private static void writeTotals(DataOutput out, Totals t) throws IOException {
        out.writeDouble(t.revenue);
        out.writeDouble(t.wages);
        out.writeDouble(t.supplies);
        out.writeDouble(t.rent);
        out.writeDouble(t.workHours);
        for (long n : new long[] {t.produced, t.sold, t.received, t.missedWages, t.missedRent})
            out.writeLong(n);
    }

    public static List<Record> read(DataInput in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 512) throw new IOException("Invalid business count");
        var result = new ArrayList<Record>();
        var ids = new HashSet<Integer>();
        for (int i = 0; i < count; i++) {
            int b = in.readInt(), c = in.readInt();
            long start = positive(in);
            double progress = in.readDouble();
            if (b < 1
                    || c < 1
                    || !ids.add(b)
                    || !Double.isFinite(progress)
                    || progress < 0
                    || progress >= 1) throw new IOException("Invalid business account");
            Totals total = readTotals(in);
            Day today = readDay(in);
            int n = in.readInt();
            if (n < 0 || n > 7 || today.day < start)
                throw new IOException("Invalid business history");
            var history = new ArrayList<Day>();
            long previous = start - 1;
            for (int j = 0; j < n; j++) {
                Day d = readDay(in);
                if (d.day <= previous || d.day >= today.day)
                    throw new IOException("Invalid business day");
                history.add(d);
                previous = d.day;
            }
            result.add(new Record(b, c, start, progress, total, today, history));
        }
        return result;
    }

    private static long positive(DataInput in) throws IOException {
        long n = in.readLong();
        if (n < 1 || n > 1_000_000_000) throw new IOException("Invalid business day");
        return n;
    }

    private static Day readDay(DataInput in) throws IOException {
        return new Day(positive(in), readTotals(in));
    }

    private static double money(DataInput in) throws IOException {
        double v = in.readDouble();
        if (!Double.isFinite(v) || v < 0 || v > 1e12)
            throw new IOException("Invalid business value");
        return v;
    }

    private static long count(DataInput in) throws IOException {
        long v = in.readLong();
        if (v < 0 || v > 1_000_000_000_000L) throw new IOException("Invalid business counter");
        return v;
    }

    private static Totals readTotals(DataInput in) throws IOException {
        return new Totals(
                money(in), money(in), money(in), money(in), money(in), count(in), count(in),
                count(in), count(in), count(in));
    }
}
