package de.codemakers.jconsole;

import de.codemakers.base.action.Action;
import de.codemakers.base.scripting.JavaScriptEngine;
import de.codemakers.base.scripting.JavaScriptEngineBuilder;
import de.codemakers.base.util.tough.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    
    public static final String VERSION = "0.1";
    public static final String VARIABLE_PATTERN_STRING = "\\$\\{%s}";
    public static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{(.+)}");
    public static final char ESCAPE_CHAR = '\\';
    public static final char WHITESPACE_ESCAPE_CHAR = '\"';
    
    private static final JavaScriptEngineBuilder JAVA_SCRIPT_ENGINE_BUILDER = new JavaScriptEngineBuilder();
    private static JavaScriptEngine JAVA_SCRIPT_ENGINE = null;
    private static String COMMAND_PREFIX = "/";
    private static final Map<String, Tough<?, ?>> COMMANDS = new ConcurrentHashMap<>();
    private static final Map<String, Object> DATA = new ConcurrentHashMap<>();
    private static final AtomicInteger COUNTER = new AtomicInteger(1);
    
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
        initStandardCommands();
        initScriptEngine();
        if (nogui) {
            initNogui();
        } else {
            System.out.println("!nogui");
        }
    }
    
    private static final void initStandardCommands() {
        final ToughRunnable command_exit = () -> System.exit(0);
        COMMANDS.put("exit", command_exit);
        COMMANDS.put("quit", command_exit);
        COMMANDS.put("q", command_exit);
        COMMANDS.put("stop", command_exit);
        final ToughFunction command_echo = (arguments) -> arguments;
        COMMANDS.put("echo", command_echo);
        final ToughFunction command_set = (arguments_) -> {
            final Object[] arguments = resolveArguments("" + arguments_);
            if (arguments.length != 2) {
                return new Response("This command needs exactly 2 arguments!", true, false, true, null);
            }
            final String key = "" + arguments[0];
            final Object value = arguments[1];
            DATA.put(key, value);
            return new Response(String.format("Setted \"%s\" to \"%s\"", key, value), true, false, false, null);
        };
        COMMANDS.put("set", command_set);
        final ToughFunction command_get = (arguments_) -> new Response(DATA.get(arguments_), true, false, false, null);
        COMMANDS.put("get", command_get);
    }
    
    private static final Object[] resolveArguments(String arguments_) {
        final List<Object> arguments = new CopyOnWriteArrayList<>();
        if (arguments_ != null && !arguments_.isEmpty()) {
            final char[] chars = arguments_.toCharArray();
            boolean escaped = false;
            String temp = null;
            for (int i = 0; i < chars.length; i++) {
                final char c = chars[i];
                if (c == ESCAPE_CHAR) {
                    if (temp == null) {
                        temp = "";
                    }
                    i++;
                    temp += chars[i];
                    continue;
                } else if (c == WHITESPACE_ESCAPE_CHAR) {
                    escaped = !escaped;
                } else if (c == ' ') {
                    if (escaped) {
                        temp += c;
                    } else {
                        if (temp != null) {
                            arguments.add(resolveArgument(temp));
                            temp = null;
                        }
                    }
                } else {
                    if (temp == null) {
                        temp = "";
                    }
                    temp += c;
                }
            }
            if (temp != null) {
                arguments.add(resolveArgument(temp));
            }
        }
        return arguments.toArray();
    }
    
    private static final Object resolveArgument(String argument) {
        if (argument == null || argument.isEmpty()) {
            return null;
        }
        final Matcher matcher = VARIABLE_PATTERN.matcher(argument);
        if (matcher.matches()) {
            return DATA.get(matcher.group(1));
        } else if (!DATA.isEmpty()) {
            for (Map.Entry<String, Object> entry : DATA.entrySet()) {
                argument = argument.replaceAll(String.format(VARIABLE_PATTERN_STRING, entry.getKey()), "" + entry.getValue());
            }
        }
        return argument;
    }
    
    private static final void initNogui() {
        final Thread thread = new Thread(() -> {
            try {
                final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
                String line = null;
                while ((line = bufferedReader.readLine()) != null) {
                    final String line_ = line;
                    Action.ofToughRunnable(() -> {
                        final Response response = handleInput(line_);
                        if (response != null) {
                            final Object data = response.getResponse();
                            String print = "" + data;
                            if (response.isSave()) {
                                String name = response.getName();
                                if (name == null) {
                                    name = "$" + COUNTER.getAndAdd(1);
                                }
                                DATA.put(name, data);
                                JAVA_SCRIPT_ENGINE.put(name, data);
                                print = String.format("%s ==> %s", name, data);
                            }
                            if (response.isPrint()) {
                                (response.isError() ? System.err : System.out).println(print);
                            }
                        }
                    }).queue();
                }
                bufferedReader.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        thread.start();
        System.out.println("Java Console V" + VERSION);
    }
    
    private static final void initScriptEngine() {
        JAVA_SCRIPT_ENGINE = JAVA_SCRIPT_ENGINE_BUILDER.build();
    }
    
    private static final Response handleInput(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        if (input.startsWith(COMMAND_PREFIX)) {
            return handleCommand(input.substring(COMMAND_PREFIX.length()));
        }
        final AtomicReference<Throwable> throwable = new AtomicReference<>(null);
        final Object response = JAVA_SCRIPT_ENGINE.executeLarge(input).direct((t) -> throwable.set(t));
        return new Response(throwable == null ? response : throwable, true, response != null, throwable != null, null); //FIXME noch das mit dem auto_add_return und auto_save_responses machen
    }
    
    private static final Response handleCommand(String command_string) {
        if (command_string == null || command_string.isEmpty()) {
            return null;
        }
        final int whitespaceIndex = command_string.indexOf(" ");
        final String invoker = whitespaceIndex != -1 ? command_string.substring(0, whitespaceIndex) : command_string;
        command_string = (whitespaceIndex != -1) ? command_string.substring(invoker.length() + 1) : null;
        final Tough<?, ?> tough = COMMANDS.get(invoker);
        if (tough == null) {
            return new Response(String.format("Command \"%s\" not found!", invoker), true, false, true, null);
        }
        Object response = null;
        if (tough.canSupply()) {
            if (tough.canConsume()) {
                if (command_string == null) {
                    return new Response(String.format("Command \"%s\" does take arguments!", invoker), true, false, true, null);
                }
                response = ((ToughFunction<Object, Object>) tough).applyWithoutException(command_string);
            } else {
                if (command_string != null) {
                    return new Response(String.format("Command \"%s\" does not take arguments!", invoker), true, false, true, null);
                }
                response = ((ToughSupplier<Object>) tough).getWithoutException();
            }
        } else {
            if (tough.canConsume()) {
                if (command_string == null) {
                    return new Response(String.format("Command \"%s\" does take arguments!", invoker), true, false, true, null);
                }
                ((ToughConsumer<Object>) tough).acceptWithoutException(command_string);
            } else {
                if (command_string != null) {
                    return new Response(String.format("Command \"%s\" does not take arguments!", invoker), true, false, true, null);
                }
                ((ToughRunnable) tough).runWithoutException();
            }
        }
        if (response instanceof Response) {
            return (Response) response;
        }
        return new Response(response, true, true, false, null);
    }
    
}
