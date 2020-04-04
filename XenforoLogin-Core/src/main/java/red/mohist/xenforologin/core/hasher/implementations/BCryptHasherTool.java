/*
 * This file is part of XenforoLogin, licensed under the GNU Lesser General Public License v3.0 (LGPLv3).
 *
 * You are not permitted to interfere any protection that prevents loading in CatServer
 *
 * Copyright (c) 2020 Mohist-Community.
 *
 */

package red.mohist.xenforologin.core.hasher.implementations;

import org.mindrot.jbcrypt.BCrypt;
import red.mohist.xenforologin.core.hasher.HasherTool;

public class BCryptHasherTool extends HasherTool {
    public BCryptHasherTool(int saltLength) {
        super(saltLength);
    }

    @Override
    public String hash(String data) {
        return BCrypt.hashpw(data, BCrypt.gensalt());
    }

    @Override
    public boolean verify(String hash, String data) {
        return BCrypt.checkpw(data, hash);
    }
}