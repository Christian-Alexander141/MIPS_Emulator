package assembler;

import java.util.List;

public class AssemblerException extends Exception {

    private final List<String> errors;

    public AssemblerException(List<String> errors) {
        super(String.join(System.lineSeparator(), errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
