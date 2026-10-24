package com.jobtrace.jobmarket.application;

import java.util.UUID;

public interface CampaignFavoriteCommand {

    boolean setFavorite(String ownerId, UUID campaignId, boolean favorite);
}
