/**
 * Copyright (c) 2022, Samsung Electronics Co., Ltd. All rights reserved.
 *
 * Use of this source code is governed by a MIT license that can be
 * found in the LICENSE file.
 */

package com.lpvs.entity.enums;

public enum LPVSPullRequestAction {
    OPEN("opened"),
    REOPEN("reopened"),
    CLOSE("closed"),
    UPDATE("synchronize"),
    RESCAN("rescan");

    private final String type;

    LPVSPullRequestAction(final String type) {
        this.type = type;
    }

    public String getPullRequestAction() {
        return type;
    }

    public static LPVSPullRequestAction convertFrom(String action) {
        if (action.equals(OPEN.getPullRequestAction())) {
            return OPEN;
        } else if (action.equals(REOPEN.getPullRequestAction())) {
            return REOPEN;
        } else if (action.equals(CLOSE.getPullRequestAction())) {
            return CLOSE;
        } else if (action.equals(UPDATE.getPullRequestAction())) {
            return UPDATE;
        } else if (action.equals(RESCAN.getPullRequestAction())) {
            return RESCAN;
        } else {
            return null;
        }
    }
}
