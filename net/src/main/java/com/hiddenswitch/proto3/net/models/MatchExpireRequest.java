package com.hiddenswitch.proto3.net.models;

import java.io.Serializable;

/**
 * Created by bberman on 12/6/16.
 */
public class MatchExpireRequest implements Serializable {
	public String gameId;

	public MatchExpireRequest(String gameId) {
		this.gameId = gameId;
	}
}