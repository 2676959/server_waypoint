import com.sun.tools.attach.VirtualMachine;

/** Attaches only to the child PID supplied by the runner. */
public class Attach {
    public static void main(String[] args) throws Exception {
        VirtualMachine machine = VirtualMachine.attach(args[0]);
        try {
            machine.loadAgent(args[1], args[2]);
        } finally {
            machine.detach();
        }
    }
}
