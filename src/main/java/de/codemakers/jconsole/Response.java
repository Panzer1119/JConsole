package de.codemakers.jconsole;

public class Response {
    
    private final Object response;
    private final boolean print;
    private final boolean save;
    private final boolean error;
    private final String name;
    
    public Response(Object response, boolean print, boolean save, boolean error, String name) {
        this.response = response;
        this.print = print;
        this.save = save;
        this.error = error;
        this.name = name;
    }
    
    public final Object getResponse() {
        return response;
    }
    
    public final boolean isPrint() {
        return print;
    }
    
    public final boolean isSave() {
        return save;
    }
    
    public final boolean isError() {
        return error;
    }
    
    public final String getName() {
        return name;
    }
    
}
