package org.ga4gh.refcloud.api.passport.passportuser;

import org.springframework.stereotype.Service;

@Service
public class PassportUserService {

    private final PassportUserRepository passportUserRepository;

    public PassportUserService(PassportUserRepository passportUserRepository) {
        this.passportUserRepository = passportUserRepository;
    }

    public PassportUser savePassportUser(PassportUser passportUser) {
        return passportUserRepository.save(passportUser);
    }
}
