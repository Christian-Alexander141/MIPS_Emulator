package CPU;

import java.util.List;

public abstract class AbstractCPU implements CPU {



    @Override
    public abstract void run(List<String> instructions);

    // Gemeinsame Methoden (optional)


}
