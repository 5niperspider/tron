/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package com.github.mrpaulblack.tron;

import org.json.JSONObject;

public class Game implements GameController {

    
    public String getGreeting() {
        return "Hello World from UPD Test Server!";
    }

    public static void main(String[] args) {
        System.out.println(new Game().getGreeting());
    }

    @Override
    public boolean setSettings(JSONObject settings) {
        // TODO Auto-generated method stub
        return false;
    }

    //Tail lenght muss hier gespeichert werdne
    //Spielfeldgröße hier speichern
}
