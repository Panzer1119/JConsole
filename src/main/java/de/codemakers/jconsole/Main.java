package de.codemakers.jconsole;

public class Main {
    
    public static final void main(String[] args) {
        boolean nogui = false;
        if (args.length == 1) {
            if (args[0].startsWith("-")) {
                args[0] = args[0].substring(1);
            }
            if (args[0].equalsIgnoreCase("nogui")) {
                nogui = true;
            }
        }
        System.out.println("Hi!");
        if (nogui) {
            System.out.println("nogui");
        } else {
            System.out.println("!nogui");
        }
    }
    
}
