/*
 * Copyright 2020 Mohist-Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package red.mohist.sodionauth.core;

import red.mohist.sodionauth.core.asyncs.CanJoin;
import red.mohist.sodionauth.core.asyncs.Login;
import red.mohist.sodionauth.core.asyncs.Register;
import red.mohist.sodionauth.core.authbackends.AuthBackendSystems;
import red.mohist.sodionauth.core.enums.ResultType;
import red.mohist.sodionauth.core.enums.StatusType;
import red.mohist.sodionauth.core.interfaces.PlatformAdapter;
import red.mohist.sodionauth.core.modules.AbstractPlayer;
import red.mohist.sodionauth.core.modules.LocationInfo;
import red.mohist.sodionauth.core.protection.SecureSystems;
import red.mohist.sodionauth.core.utils.*;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SodionAuthCore {

    public static SodionAuthCore instance;
    public PlatformAdapter api;
    public ConcurrentMap<UUID, StatusType> logged_in;
    public LocationInfo default_location;
    private Connection connection;
    private LinkedBlockingQueue<CanJoin> canJoinTask;
    private LinkedBlockingQueue<Login> LoginTask;
    private LinkedBlockingQueue<Register> registerTask;

    public SodionAuthCore(PlatformAdapter platformAdapter) {

        {
            // WARN You are not permitted to interfere any protection that prevents loading in CatServer
            String a0 = "WARN You are not permitted to interfere any protection that prevents loading in CatServer";
            try {
                if (Class.forName("catserver.server.CatServer") != null) {
                    // WARN You are not permitted to interfere any protection that prevents loading in CatServer
                    String a1 = "WARN You are not permitted to interfere any protection that prevents loading in CatServer";
                    System.out.println("======================== [ Security Check ] ========================");
                    System.out.println("万分抱歉，本插件未对CatServer作任何测试，尚不能保证能正常使用。");
                    System.out.println("出于保护您的服务器安全的考虑，XenforoLogin已经停止载入。");
                    System.out.println("如果你需要使用本插件，建议使用Mohist服务端，我们完成了良好的适配。");
                    System.out.println("We are sorry that this plugin is not tested against CatServer at all");
                    System.out.println("It can cause some major issues preventing the plugin to work.");
                    System.out.println("Because of security issues, XenforoLogin aborted starting the server.");
                    System.out.println("If you want to use it, switch to Mohist which is more stable.");
                    System.out.println("==================================================================");
                    new Thread(() -> {
                        System.exit(-1);
                        // WARN You are not permitted to interfere any protection that prevents loading in CatServer
                        String a2 = "WARN You are not permitted to interfere any protection that prevents loading in CatServer";
                    }).start();
                    Thread.sleep(1000);
                    new Thread(() -> {
                        Runtime.getRuntime().halt(-1);
                        // WARN You are not permitted to interfere any protection that prevents loading in CatServer
                        String a3 = "WARN You are not permitted to interfere any protection that prevents loading in CatServer";
                    }).start();
                    return;
                }
                // WARN You are not permitted to interfere any protection that prevents loading in CatServer
                String a4 = "WARN You are not permitted to interfere any protection that prevents loading in CatServer";
            } catch (ClassNotFoundException ignored) {
                // WARN You are not permitted to interfere any protection that prevents loading in CatServer
                String a5 = "WARN You are not permitted to interfere any protection that prevents loading in CatServer";
            } catch (Exception e) {
                // WARN You are not permitted to interfere any protection that prevents loading in CatServer
                String a6 = "WARN You are not permitted to interfere any protection that prevents loading in CatServer";
                e.printStackTrace();
            }
            // WARN You are not permitted to interfere any protection that prevents loading in CatServer
            String a7 = "WARN You are not permitted to interfere any protection that prevents loading in CatServer";
        }

        instance = this;
        api = platformAdapter;
        logged_in = new ConcurrentHashMap<>();
        canJoinTask = new LinkedBlockingQueue<>();
        LoginTask = new LinkedBlockingQueue<>();
        registerTask = new LinkedBlockingQueue<>();
        loadConfig();

        AuthBackendSystems.reloadConfig();
        SecureSystems.reloadConfig();
        LoginTicker.register();


        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + Helper.getConfigPath("XenforoLogin.db"));
            if (!connection.getMetaData().getTables(null, null, "locations", new String[]{"TABLE"}).next()) {
                PreparedStatement pps = connection.prepareStatement("CREATE TABLE locations (uuid NOT NULL,world,x,y,z,yaw,pitch,mode,PRIMARY KEY (uuid));");
                pps.executeUpdate();
            }
            if (!connection.getMetaData().getTables(null, null, "sessions", new String[]{"TABLE"}).next()) {
                PreparedStatement pps = connection.prepareStatement("CREATE TABLE sessions (uuid NOT NULL,ip,time,PRIMARY KEY (uuid));");
                pps.executeUpdate();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        for (AbstractPlayer abstractPlayer : api.getAllPlayer()) {
            new Thread(() -> {
                String canjoin = SodionAuthCore.instance.canJoinHandle(abstractPlayer);
                if (canjoin != null) {
                    abstractPlayer.kick(canjoin);
                }
            }).start();
            onJoin(abstractPlayer);
        }
        createWorkers();
    }

    private void createWorkers() {
        new Thread(() -> {
            while (true){
                try {
                    CanJoin task = canJoinTask.take();
                    task.run(canJoinHandle(task.player));
                }catch (Throwable e){
                    e.printStackTrace();
                }
            }
        }).start();
        new Thread(() -> {
            while (true){
                try {
                    Login task = LoginTask.take();
                    task.run(AuthBackendSystems.getCurrentSystem()
                            .login(task.player, task.message));
                }catch (Throwable e){
                    e.printStackTrace();
                }
            }
        }).start();
        new Thread(() -> {
            while (true){
                try {
                    Register task = registerTask.take();
                    task.run(AuthBackendSystems.getCurrentSystem()
                            .register(task.player, task.password, task.email));
                }catch (Throwable e){
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void onDisable() {
        for (AbstractPlayer abstractPlayer : api.getAllPlayer()) {
            onQuit(abstractPlayer);
        }
        LoginTicker.unregister();
        try {
            connection.close();
        } catch (Throwable ignored) {
        }
    }

    private void loadConfig() {
        LocationInfo spawn_location = api.getSpawn(api.getDefaultWorld());
        default_location = new LocationInfo(
                Config.spawn.getWorld(api.getDefaultWorld()),
                Config.spawn.getX(spawn_location.x),
                Config.spawn.getY(spawn_location.y),
                Config.spawn.getZ(spawn_location.z),
                Config.spawn.getYaw(spawn_location.yaw),
                Config.spawn.getPitch(spawn_location.pitch)
        );
    }

    public boolean needCancelled(AbstractPlayer player) {
        return !logged_in.getOrDefault(player.getUniqueId(), StatusType.NEED_LOGIN).equals(StatusType.LOGGED_IN);
    }

    public void login(AbstractPlayer player) {
        logged_in.put(player.getUniqueId(), StatusType.LOGGED_IN);
        try {
            PreparedStatement pps = connection.prepareStatement("SELECT * FROM locations WHERE uuid=? LIMIT 1;");
            pps.setString(1, player.getUniqueId().toString());
            ResultSet rs = pps.executeQuery();
            if (!rs.next()) {
                if (Config.teleport.getTpBackAfterLogin()) {
                    LocationInfo spawn_location = api.getSpawn("world");
                    player.teleport(spawn_location);
                }
                player.setGameMode(Config.security.getDefaultGamemode());
            } else {
                if (Config.teleport.getTpBackAfterLogin()) {
                    try {
                        player.teleport(new LocationInfo(
                                rs.getString("world"),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getFloat("yaw"),
                                rs.getFloat("pitch")
                        ));
                    } catch (Exception e) {
                        LocationInfo spawn_location = api.getSpawn("world");
                        player.teleport(spawn_location);
                        Helper.getLogger().warn("Fail tp a player.Have you change the world?");
                    }
                }
                player.setGameMode(rs.getInt("mode"));
            }


        } catch (Throwable e) {
            player.setGameMode(Config.security.getDefaultGamemode());
            e.printStackTrace();
        }

        try {
            PreparedStatement pps = connection.prepareStatement("DELETE FROM sessions WHERE uuid = ?;");
            pps.setString(1, player.getUniqueId().toString());
            pps.executeUpdate();

            pps = connection.prepareStatement("INSERT INTO sessions(uuid, ip, time) VALUES (?, ?, ?);");
            pps.setString(1, player.getUniqueId().toString());
            pps.setString(2, player.getAddress().getHostAddress());
            pps.setInt(3, (int) (System.currentTimeMillis() / 1000));
            pps.executeUpdate();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        api.onLogin(player);
        Helper.getLogger().warn("Logging in " + player.getUniqueId());
        player.sendMessage(player.getLang().getSuccess());
    }

    public void message(AbstractPlayer player) {
        switch (logged_in.get(player.getUniqueId())) {
            case NEED_LOGIN:
                player.sendMessage(player.getLang().getNeedLogin());
                break;
            case NEED_REGISTER_EMAIL:
                player.sendMessage(player.getLang().getRegisterEmail());
                break;
            case NEED_REGISTER_PASSWORD:
                player.sendMessage(player.getLang().getRegisterPassword());
                break;
            case NEED_REGISTER_CONFIRM:
                player.sendMessage(player.getLang().getRegisterPasswordConfirm());
                break;
        }
    }

    public void onQuit(AbstractPlayer player) {
        LocationInfo leave_location = player.getLocation();
        if (!needCancelled(player)) {
            try {
                PreparedStatement pps = connection.prepareStatement("DELETE FROM locations WHERE uuid = ?;");
                pps.setString(1, player.getUniqueId().toString());
                pps.executeUpdate();
                pps = connection.prepareStatement("INSERT INTO locations(uuid, world, x, y, z, yaw, pitch,mode) VALUES (?, ?, ?, ?, ?, ?, ?, ?);");
                pps.setString(1, player.getUniqueId().toString());
                pps.setString(2, leave_location.world);
                pps.setDouble(3, leave_location.x);
                pps.setDouble(4, leave_location.y);
                pps.setDouble(5, leave_location.z);
                pps.setFloat(6, leave_location.yaw);
                pps.setFloat(7, leave_location.pitch);
                pps.setInt(8, player.getGameMode());
                pps.executeUpdate();
            } catch (SQLException e) {
                Helper.getLogger().warn("Fail to save location.");
            }
        }
        player.teleport(default_location);
        logged_in.remove(player.getUniqueId());
    }

    public String canLogin(AbstractPlayer player) {
        return SecureSystems.canJoin(player);
    }


    public CompletableFuture<String> canJoin(AbstractPlayer player) {
        CompletableFuture<String> can = new CompletableFuture<>();
        canJoinAsync(new CanJoin(player) {
            @Override
            public void run(String result) {
                can.complete(result);
            }
        });
        return can;
    }

    public String canJoinHandle(AbstractPlayer player) {
        if (SodionAuthCore.instance.logged_in.containsKey(player.getUniqueId())
                && SodionAuthCore.instance.logged_in.get(player.getUniqueId()) != StatusType.HANDLE) {
            return null;
        }
        SodionAuthCore.instance.logged_in.put(player.getUniqueId(), StatusType.HANDLE);

        ResultType resultType = AuthBackendSystems.getCurrentSystem()
                .join(player)
                .shouldLogin(false);
        switch (resultType) {
            case OK:
                SodionAuthCore.instance.logged_in.put(player.getUniqueId(), StatusType.NEED_LOGIN);
                return null;
            case ERROR_NAME:
                return player.getLang().getErrors().getNameIncorrect(
                        resultType.getInheritedObject());
            case NO_USER:
                if (Config.api.getAllowRegister()) {
                    SodionAuthCore.instance.logged_in.put(player.getUniqueId(), StatusType.NEED_REGISTER_EMAIL);
                    return null;
                } else {
                    return player.getLang().getErrors().getNoUser();
                }
            case UNKNOWN:
                return player.getLang().getErrors().getUnknown(resultType.getInheritedObject());
        }
        return player.getLang().getErrors().getUnknown();
    }

    public void canJoinAsync(CanJoin action) {
        SodionAuthCore.instance.logged_in.put(action.player.getUniqueId(), StatusType.HANDLE);
        canJoinTask.add(action);
    }

    public void onJoin(AbstractPlayer abstractPlayer) {
        api.sendBlankInventoryPacket(abstractPlayer);
        if (Config.teleport.getTpSpawnBeforeLogin()) {
            abstractPlayer.teleport(default_location);
        }
        if (Config.security.getSpectatorLogin()) {
            abstractPlayer.setGameMode(3);
        }
        LoginTicker.add(abstractPlayer);
        try {
            if (Config.session.getEnable()) {
                PreparedStatement pps = connection.prepareStatement("SELECT * FROM sessions WHERE uuid=? AND ip=? AND time>? LIMIT 1;");
                pps.setString(1, abstractPlayer.getUniqueId().toString());
                pps.setString(2, abstractPlayer.getAddress().getHostAddress());
                pps.setInt(3, (int) (System.currentTimeMillis() / 1000 - Config.session.getTimeout()));
                ResultSet rs = pps.executeQuery();
                if (rs.next()) {
                    abstractPlayer.sendMessage(abstractPlayer.getLang().getSession());
                    login(abstractPlayer);
                }
            }
        } catch (Throwable e) {
            Helper.getLogger().warn("Fail use session.");
            e.printStackTrace();
        }
    }

    public void onChat(AbstractPlayer player, String message) {
        StatusType status = SodionAuthCore.instance.logged_in.get(player.getUniqueId());
        switch (status) {
            case NEED_CHECK:
                player.sendMessage(player.getLang().getNeedLogin());
                break;
            case NEED_LOGIN:
                String canLogin=SecureSystems.canLogin(player);
                if (canLogin != null) {
                    player.sendMessage(canLogin);
                    return;
                }
                SodionAuthCore.instance.logged_in.put(
                        player.getUniqueId(), StatusType.HANDLE);
                LoginTask.add(new Login(player, message) {
                    @Override
                    public void run(ResultType result) {
                        ResultTypeUtils.handle(player, result.shouldLogin(true));
                    }
                });
                break;
            case NEED_REGISTER_EMAIL:
                if (isEmail(message)) {
                    logged_in.put(player.getUniqueId(), StatusType.NEED_REGISTER_PASSWORD.setEmail(message));
                    message(player);
                } else {
                    player.sendMessage(player.getLang().getErrors().getEmail());
                }
                break;
            case NEED_REGISTER_PASSWORD:
                SodionAuthCore.instance.logged_in.put(
                        player.getUniqueId(),
                        StatusType.NEED_REGISTER_CONFIRM.setEmail(status.email).setPassword(message));
                message(player);
                break;
            case NEED_REGISTER_CONFIRM:
                String canRegister=SecureSystems.canRegister(player);
                if (canRegister != null) {
                    player.sendMessage(canRegister);
                    return;
                }
                SodionAuthCore.instance.logged_in.put(
                        player.getUniqueId(), StatusType.HANDLE);
                if (message.equals(status.password)) {
                    registerTask.add(new Register(player, status.email, status.password) {
                        @Override
                        public void run(ResultType answer) {
                            boolean result = ResultTypeUtils.handle(player,
                                    answer.shouldLogin(true));
                            if (result) {
                                SodionAuthCore.instance.logged_in.put(
                                        player.getUniqueId(), StatusType.LOGGED_IN);
                            } else {
                                SodionAuthCore.instance.logged_in.put(
                                        player.getUniqueId(), StatusType.NEED_REGISTER_EMAIL);
                                SodionAuthCore.instance.message(player);
                            }
                        }
                    });
                } else {
                    player.sendMessage(player.getLang().getErrors().getConfirm());
                    SodionAuthCore.instance.logged_in.put(
                            player.getUniqueId(), StatusType.NEED_REGISTER_PASSWORD);
                    SodionAuthCore.instance.message(player);
                }
                break;
            case HANDLE:
                player.sendMessage(player.getLang().getErrors().getHandle());
                break;
        }
    }

    public boolean isEmail(String email) {
        if (null == email || "".equals(email)) {
            return false;
        }
        Pattern p = Pattern.compile("\\w+@(\\w+.)+[a-z]{2,10}");
        Matcher m = p.matcher(email);
        return m.matches();
    }
}