package com.bd.blooddonorfinder.service.auth;

import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.utils.PiiTokenizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PiiServiceImpl implements PiiService{
    private final PiiTokenizer piiTokenizer;

    public PiiServiceImpl(PiiTokenizer piiTokenizer) {
        this.piiTokenizer = piiTokenizer;
    }

    @Override
    @Transactional
    public String decryptEmail(User user) {
        if(user.getEmailToken() == null){
            throw  new IllegalStateException("User has no email token");
        }
        return  piiTokenizer.decrypt(user.getEmailToken().getId())
                .orElseThrow(()->
                        new IllegalStateException("Could not decrypt email for user : "+user.getId()));

    }

    @Override
    @Transactional
    public String decryptPhone(User user) {
        if(user.getPhoneToken() == null){
            throw  new IllegalStateException("User has no phone token");
        }
        return  piiTokenizer.decrypt(user.getPhoneToken().getId())
                .orElseThrow(()->
                        new IllegalStateException("Could not decrypt phone for user : "+user.getId()));

    }

    @Override
    public String encryptEmail(String email) {
        return null;
    }

    @Override
    public String encryptPhone(String phone) {
        return null;
    }
}
