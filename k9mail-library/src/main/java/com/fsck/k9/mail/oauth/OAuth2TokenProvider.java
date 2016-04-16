package com.fsck.k9.mail.oauth;

import com.fsck.k9.mail.AuthenticationFailedException;

public interface OAuth2TokenProvider {
    String authorizeAPI(String username) throws AuthenticationFailedException;

    String getToken(String username) throws AuthenticationFailedException;
    void invalidateToken(String username);

}
