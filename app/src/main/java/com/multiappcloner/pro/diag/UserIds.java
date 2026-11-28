package com.multiappcloner.pro.diag;

/** Chooses virtual user ids. An id is never handed out twice, so a new clone can never inherit a deleted clone's data. */
public final class UserIds {
    /** @param dbMax highest id stored in the clone database (-1 if none) @param counter highest id ever allocated (-1 if none) */
    public static int next(int dbMax, int counter) {
        return Math.max(dbMax, counter) + 1;
    }
}
