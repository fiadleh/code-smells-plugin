package com.github.fiadleh.codesmellsplugin.services;
import com.intellij.openapi.diagnostic.Logger;

/**
 * A timer class used for debugging
 *
 * @author Firas Adleh
 */
public class CodesmellTimer {
    private final String message;
    private String className;
    private long start = 0;
    private long finish = 0;

    /**
     * Initialize the timer with its main message
     *
     * @param message
     */
    public CodesmellTimer(String message){
        this.message = message;
    }

    public void startTimer(){
        start = System.nanoTime();
    }

    public void stopTimer(){
        finish = System.nanoTime();
    }

    /**
     * Time differance in microseconds
     *
     * @return long Time differance in microseconds
     */
    public long getDurationMicroseconds(){
        return (this.finish - this.start)/1000;
    }

    public void printMessage(){
        Logger.getInstance("#Timer").warn(this.message + ", Duration = "+this.getDurationMicroseconds()+" micro, className = "+this.className);
    }

    /**
     * Update the current class name for this timer
     * @param className
     */
    public void setClassName(String className){this.className = className;}
}
