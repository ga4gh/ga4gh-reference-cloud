package org.ga4gh.refcloud.api.webhook.kratos;

import org.ga4gh.refcloud.api.passport.passportuser.PassportUser;
import org.ga4gh.refcloud.api.passport.passportuser.PassportUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/kratos")
public class KratosWebhookController {

    private final PassportUserService passportUserService;

    public KratosWebhookController(PassportUserService passportUserService) {
        this.passportUserService = passportUserService;
    }

    @PostMapping("/registration")
    public ResponseEntity<Void> registerPassportUser(@RequestBody KratosRegistrationPayload payload) {
        System.out.println("***");
        System.out.println("You have hit the register user endpoint");
        PassportUser passportUser = new PassportUser();
        passportUser.setId(payload.id());
        passportUserService.savePassportUser(passportUser);
        System.out.println("***");
        return ResponseEntity.noContent().build();
    }
    
}
