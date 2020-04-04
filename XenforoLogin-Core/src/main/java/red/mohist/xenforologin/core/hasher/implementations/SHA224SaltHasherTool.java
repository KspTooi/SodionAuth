/*
 * This file is part of XenforoLogin, licensed under the GNU Lesser General Public License v3.0 (LGPLv3).
 *
 * You are not permitted to interfere any protection that prevents loading in CatServer
 *
 * Copyright (c) 2020 Mohist-Community.
 *
 */

package red.mohist.xenforologin.core.hasher.implementations;

public class SHA224SaltHasherTool extends SHA224HasherTool {
    public SHA224SaltHasherTool(int saltLength) {
        super(saltLength);
    }

    @Override
    public boolean needSalt() {
        return true;
    }
}