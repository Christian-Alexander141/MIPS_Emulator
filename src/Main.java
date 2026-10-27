import loader.Loader;

public class Main {
    public static void main(String[] args) {
       
            // Mit Argumenten (z. B. "complex_mul_acc.asm --latency 4"): ohne Rückfragen ausführen
            if (args.length > 0) {
                System.exit(Loader.runBatch(args));
            }

            Loader loader = new Loader();
            loader.load();

        }
}
