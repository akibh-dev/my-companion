import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Catalogue {
    static final class Reminder {
        final long id;
        final String title;
        final boolean enabled;

        Reminder(long id, String title, boolean enabled) {
            if (id <= 0) throw new IllegalArgumentException("ID must be positive");
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Title must not be blank");
            }
            this.id = id;
            this.title = title.trim();
            this.enabled = enabled;
        }
    }

    interface ReminderStore {
        void save(Reminder reminder);
        List<Reminder> all();
    }

    static final class MemoryReminderStore implements ReminderStore {
        private final Map<Long, Reminder> items = new LinkedHashMap<>();

        @Override
        public void save(Reminder reminder) {
            items.put(reminder.id, reminder);
        }

        @Override
        public List<Reminder> all() {
            return List.copyOf(items.values());
        }
    }

    static List<Reminder> search(ReminderStore store, String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return store.all().stream()
                .filter(r -> r.title.toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        ReminderStore store = new MemoryReminderStore();
        store.save(new Reminder(1, " Tea ", true));
        List<Reminder> snapshot = store.all();
        store.save(new Reminder(2, "Water", false));
        check(snapshot.size() == 1, "snapshot is independent");
        check(store.all().size() == 2, "two reminders");
        check(search(store, " TEA ").size() == 1, "normalized search");
        store.save(new Reminder(1, "Tea break", true));
        check(store.all().size() == 2, "same ID replaces");
        check(store.all().get(0).title.equals("Tea break"), "replacement value");
        for (Reminder reminder : store.all()) {
            System.out.println(reminder.id + ": " + reminder.title);
        }
        System.out.println("All Java catalogue checks passed");
    }
}
