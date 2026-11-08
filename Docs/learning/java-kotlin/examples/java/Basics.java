public final class Basics {
    static long delayMillis(int minutes) {
        if (minutes < 0 || minutes > 40_000) {
            throw new IllegalArgumentException("minutes must be 0..40000");
        }
        return 60_000L * minutes;
    }

    static int countPositive(int[] values) {
        int count = 0;
        for (int value : values) {
            if (value > 0) count++;
        }
        return count;
    }

    static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        int minutes = 5;
        final String title = "Drink water";
        System.out.println(title + " in " + minutes + " minutes");
        check(delayMillis(minutes) == 300_000L, "five minutes");
        check(delayMillis(40_000) == 2_400_000_000L, "long arithmetic");
        check(countPositive(new int[] {0, 5, 10, -1}) == 2, "positive count");
        check("tea".equals(new String("tea")), "text equality");
        boolean rejected = false;
        try {
            delayMillis(-1);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "invalid input rejected");
        System.out.println("All Java basics checks passed");
    }
}
