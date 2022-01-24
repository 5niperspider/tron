/*
 * can be tested with nc -u 127.0.0.1 3000 and sending data by typing in terminal and pressing return
 * TODO write docs
 * 
 * example paylods:
 * ### client hello
 * {"type":"hello","data":{"protocolVersion":1}}
 * {"type":"hello","data":{"protocolVersion":-1,"clientName":"tron_cli_client","clientVersion":0.1}}
 * {"type":"hello","data":{"protocolVersion":1,"clientName":"tron_cli_client","clientVersion":0.1}}
 * 
 * ### client hello
 * {"type":"register","data":{"protocolVersion":1}}
 * {"type":"register","data":{"sessionID":""}}
 * {"type":"register","data":{"sessionID":"test"}}
 *
 * ### type errors
 * {"type":"test","data":{"test":1}}
 * {"data":{"protocolVersion":1}}
 */
package com.github.mrpaulblack.tron;

import java.net.URI;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map.Entry;

import org.json.*;



public class ServerController {
    private Server server;
    // TODO this is basically the definition of a memory leak since clients do not get deleted; maybe fixable with a timer that deletes clients in case of timeout!?
    private HashMap<URI, UUID> clientID = new HashMap<URI, UUID>();
    private HashMap<URI, MsgType> clientState = new HashMap<URI, MsgType>();
    private HashMap<URI, String> clientSession = new HashMap<URI, String>();
    private HashMap<String, GameController> session = new HashMap<String, GameController>();
    private String serverName = "tron_server";
    private float serverVersion = 0.1f;
    private int protocolVersion = 1;



    // server to support sending messages to clients
    public ServerController(Server server) {
        this.server = server;
    }



    // send welcome to client
    private void sendWelcome(URI client) throws Exception {
        JSONObject json = new JSONObject();
        JSONObject data = new JSONObject();

        json.put("type", MsgType.WELCOME.toString());
        data.put("serverName", serverName);
        data.put("serverVersion", serverVersion);
        json.put("data", data);

        server.send(client, json.toString());
    }



    // send session settings to client
    private void sendSessionSettings(URI client) throws Exception {
        JSONObject json = new JSONObject();
        JSONObject data = new JSONObject();

        json.put("type", MsgType.SESSIONSETTINGS.toString());
        data.put("settings", GameController.getSettings());
        json.put("data", data);

        server.send(client, json.toString());
    }



    // send error with optional error message to client
    private void sendError(URI client, MsgError error, String message) throws Exception {
        JSONObject json = new JSONObject();
        JSONObject data = new JSONObject();

        if (message != null) {
            json.put("message", message);
        }

        json.put("type", MsgType.ERROR.toString());
        data.put("error", error.toString());
        json.put("data", data);

        server.send(client, json.toString());
    }



    // broadcast update to all clients in session
    private void broadcastUpdate(String sessionID) throws Exception {
        JSONObject json = new JSONObject();
    
        json.put("type", MsgType.UPDATE);
        // TODO placeholder for data method from game
        json.put("data", GameController.getSettings());

        for (Entry<URI, String> entry : clientSession.entrySet()) {
            if (entry.getValue().equals(sessionID)) {
                server.send(entry.getKey(), json.toString());
            }
        }
    }



    // decode payload from a client
    protected void decode(URI client, String payload) {
        try {
            JSONObject json = new JSONObject(payload);
            JSONObject data = new JSONObject(json.get("data").toString());

            if (json.has("type")) {
                // client hello
                if (json.getString("type").equals(MsgType.HELLO.toString())) {
                    if (data.has("protocolVersion") && data.has("clientName") && data.has("clientVersion")) {
                        if (data.getInt("protocolVersion") <= protocolVersion && data.getInt("protocolVersion") > 0) {
                            clientID.put(client, UUID.randomUUID());
                            clientState.put(client, MsgType.HELLO);
                            LogController.log(Log.DEBUG, "{" + clientID.get(client) + "} has send HELLO");
                            sendWelcome(client);
                        }
                        else {
                            sendError(client, MsgError.UNSUPPORTEDPROTOCOLVERSION, "Your protocol version is not supported by the server. The server supports version " + protocolVersion + ".");
                            throw new IllegalArgumentException("Protocol version not supported by server");
                        }
                    }
                    else {
                        sendError(client, MsgError.UNSUPPORTEDMESSAGETYPE, "Your payload is missing at least one required keys: protocolVersion, clientName, clientVersion.");
                        throw new IllegalArgumentException("payload is missing at least one required keys: protocolVersion, clientName, clientVersion");
                    }
                }

                // client register
                else if (json.getString("type").equals(MsgType.REGISTER.toString()) && clientState.get(client) == MsgType.HELLO) {
                    if (data.has("sessionID")) {
                        if (data.getString("sessionID").length() <= 64 && data.getString("sessionID").length() >= 1) {
                            // create new session if it does not exist already
                            if (!session.containsKey(data.getString("sessionID"))) {
                                session.put(data.getString("sessionID"), null);
                                clientSession.put(client, data.getString("sessionID"));
                                LogController.log(Log.DEBUG, "{" + clientID.get(client) + "} created new session with session ID: " + clientSession.get(client));
                                clientState.put(client, MsgType.SESSIONDATA);
                                sendSessionSettings(client);
                            }
                            // otherwise register in existing session
                            else if (session.get(data.getString("sessionID")) != null) {
                                clientSession.put(client, data.getString("sessionID"));
                                LogController.log(Log.DEBUG, "{" + clientID.get(client) + "} has registred to existing session with ID: " + clientSession.get(client));
                                clientState.put(client, MsgType.REGISTER);
                                broadcastUpdate(clientSession.get(client));
                            }
                            else {
                                sendError(client, MsgError.UNKNOWN, "The session is still getting configured.");
                                throw new IllegalArgumentException("session is not configured yet");
                            }
                        }
                        else {
                            sendError(client, MsgError.UNSUPPORTEDMESSAGETYPE, "Your session ID is to long or to small. The server allows between 1 and 64 char.");
                            throw new IllegalArgumentException("session ID needs to be between 1 and 64 char");
                        }
                    }
                    else {
                        sendError(client, MsgError.UNSUPPORTEDMESSAGETYPE, "Your payload is missing the required key: sessionID.");
                        throw new IllegalArgumentException("payload is missing the required key: sessionID");
                    }
                }

                // client session settings
                else if (json.getString("type").equals(MsgType.SESSIONDATA.toString()) && clientState.get(client) == MsgType.SESSIONDATA) {
                    // TODO sanitizer start sessiondata by client
                    GameController tempGame = new Game(protocolVersion, protocolVersion, protocolVersion, protocolVersion);
                    session.replace(clientSession.get(client), tempGame);
                    clientState.put(client, MsgType.REGISTER);
                    broadcastUpdate(clientSession.get(client));
                }

                // client lobby settings
                else if (json.getString("type").equals(MsgType.LOBBYDATA.toString()) && clientState.get(client) == MsgType.REGISTER) {
                    if (data.has("ready")) {
                        // player state ready
                        if (data.getBoolean("ready")) {
                            if (data.has("name") && data.has("color")) {
                                if (data.getString("name").length() <= 64 && data.getString("name").length() >= 1) {
                                    if (PlayerColor.toPlayerColor(data.getString("color")) != null && PlayerColor.toPlayerColor(data.getString("color")) != PlayerColor.UNDEFINED) {
                                        // TODO call game with color -> game needs to return if successfull or not as boolean and also if game starts to timer can run for update move cicle
                                        session.get(clientSession.get(client)).ready(clientID.get(client), PlayerColor.toPlayerColor(data.getString("color")), data.getString("name"));
                                    }
                                    else {
                                        sendError(client, MsgError.UNSUPPORTEDMESSAGETYPE, "Your defined player color does not exist on the server as an option.");
                                        throw new IllegalArgumentException("player color does not exist");
                                    }
                                }
                                else {
                                    sendError(client, MsgError.UNSUPPORTEDMESSAGETYPE, "Your name is to long or to small. The server allows between 1 and 64 char.");
                                    throw new IllegalArgumentException("name needs to be between 1 and 64 char");
                                }
                            }
                            else {
                                sendError(client, MsgError.UNSUPPORTEDMESSAGETYPE, "Your payload is missing at least one required keys: name, color.");
                                throw new IllegalArgumentException("payload is missing at least one required keys: name, color");
                            }
                        }
                        // player state unready
                        else {
                            session.get(clientSession.get(client)).unready(clientID.get(client));
                        }
                    }
                    else {
                        sendError(client, MsgError.UNSUPPORTEDMESSAGETYPE, "Your payload is missing the required keys: ready.");
                        throw new IllegalArgumentException("payload is missing the required keys: ready");
                    }
                }

                // client move
                else if (json.getString("type").equals(MsgType.MOVE.toString()) && clientState.get(client) == MsgType.REGISTER) {
                    //start move by client
                }

                // client message
                else if (json.getString("type").equals(MsgType.MESSAGE.toString())) {
                    if (data.getBoolean("broadcast")) {
                        // TODO impl. broadcast of message
                        LogController.log(Log.INFO, "Message from " + clientID.get(client) + ": " + json.getString("message"));
                    }
                    else { LogController.log(Log.INFO, "Message from " + clientID.get(client) + ": " + json.getString("message")); }
                }

                // client error
                else if (json.getString("type").equals(MsgType.ERROR.toString())) {
                    LogController.log(Log.ERROR, "{" + clientID.get(client) + "} Error from client: " + data.get("message"));
                    throw new IllegalArgumentException("Error message from client recieved");
                }

                // type is not defined in API spec error
                else {
                    sendError(client, MsgError.UNSUPPORTEDMESSAGETYPE, "Your message type is not supported by the server or API request out of order.");
                    throw new IllegalArgumentException("Unsupported message type or out of order");
                }
            }

            // payload is missing type key error
            else {
                sendError(client, MsgError.UNKNOWN, "Your payload is missing the type key.");
                throw new IllegalArgumentException("Type key is missing from request");
            }

        // remove client from session if exception
        } catch (Exception e) {
            LogController.log(Log.ERROR, "{" + clientID.get(client) + "} " + e.toString());
            clientID.remove(client);
            clientState.remove(client);
            clientSession.remove(client);
        }
    }
}
