package com.craftinginterpreters.lox;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import sun.misc.Signal;
import sun.misc.SignalHandler;

class SigHandler {
    static Map<String,SigHandler> handlerMap = new HashMap<>();
    static Map<String,Boolean> failedSignalsRegistered = new HashMap<>();

    final private String sigName;
    final private LoxCallable callback;
    final private Interpreter interp;
    protected SigHandler next;

    SigHandler(String sigName, LoxCallable callback, Interpreter interp) {
        this.sigName = sigName;
        this.callback = callback;
        this.interp = interp;
        this.next = null;
    }

    protected void call() {
        interp.evaluateCall(callback, LoxUtil.EMPTY_ARGS,
            LoxUtil.EMPTY_KWARGS, null);
    }

    static void register(String sigName, LoxCallable callback, Interpreter interp) {
        SigHandler handler = new SigHandler(sigName, callback, interp);
        if (handlerMap.containsKey(sigName)) {
            SigHandler lastH = handlerMap.get(sigName);
            while (lastH.next != null) {
                lastH = lastH.next;
            }
            lastH.next = handler;
        } else {
            if (failedSignalsRegistered.get(sigName) == (Boolean)true) {
                System.err.println("<fn Signal.handle> failed to register signal: '" +
                    sigName + "'");
                return;
            }
            boolean failedToRegister = false;
            // try to register the signal
            try {
                Signal.handle(new Signal(sigName), new SignalHandler() {
                    public void handle(Signal sig) {
                        SigHandler.runCallbacks(sig.getName());
                    }
                });
            } catch (Throwable t) {
                System.err.println("<fn Signal.handle> failed to register signal: '" +
                    sigName + "'");
                failedToRegister = true;
            }
            if (failedToRegister) {
                failedSignalsRegistered.put(sigName, true);
                return;
            }
            handlerMap.put(sigName, handler);
        }
    }

    static void runCallbacks(String sigName) {
        SigHandler handler = handlerMap.get(sigName);
        while (handler != null) {
            handler.call();
            handler = handler.next;
        }
    }

}
