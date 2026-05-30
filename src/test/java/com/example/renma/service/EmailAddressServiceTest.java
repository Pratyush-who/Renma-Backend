package com.example.renma.service;

import com.example.renma.service.EmailAddressService.NormalizedEmail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAddressServiceTest {

    private final EmailAddressService emailAddressService = new EmailAddressService();

    @Test
    void normalizesCaseAndSpacesWithoutChangingDotsOrPlusAliases() {
        NormalizedEmail email = emailAddressService.normalize("  Pratyush.Dev+tech@GMAIL.com  ");

        assertThat(email).isNotNull();
        assertThat(email.normalizedEmail()).isEqualTo("pratyush.dev+tech@gmail.com");
        assertThat(email.canonicalEmail()).isEqualTo("pratyush.dev+tech@gmail.com");
    }

    @Test
    void doesNotTreatGooglemailAsGmail() {
        NormalizedEmail email = emailAddressService.normalize("pratyush.dev+college@googlemail.com");

        assertThat(email).isNotNull();
        assertThat(email.canonicalEmail()).isEqualTo("pratyush.dev+college@googlemail.com");
    }

    @Test
    void keepsGmailDotVariantsAsDifferentCanonicalEmails() {
        NormalizedEmail plain = emailAddressService.normalize("pratyushmehra2005@gmail.com");
        NormalizedEmail oneDot = emailAddressService.normalize("pratyush.mehra2005@gmail.com");
        NormalizedEmail anotherDot = emailAddressService.normalize("pratyushmehra.2005@gmail.com");
        NormalizedEmail manyDots = emailAddressService.normalize("p.r.a.t.y.u.s.h.m.e.h.r.a.2005@gmail.com");

        assertThat(plain).isNotNull();
        assertThat(oneDot).isNotNull();
        assertThat(anotherDot).isNotNull();
        assertThat(manyDots).isNotNull();
        assertThat(plain.canonicalEmail()).isEqualTo("pratyushmehra2005@gmail.com");
        assertThat(oneDot.canonicalEmail()).isEqualTo("pratyush.mehra2005@gmail.com");
        assertThat(anotherDot.canonicalEmail()).isEqualTo("pratyushmehra.2005@gmail.com");
        assertThat(manyDots.canonicalEmail()).isEqualTo("p.r.a.t.y.u.s.h.m.e.h.r.a.2005@gmail.com");
        assertThat(plain.canonicalEmail())
                .isNotEqualTo(oneDot.canonicalEmail())
                .isNotEqualTo(anotherDot.canonicalEmail())
                .isNotEqualTo(manyDots.canonicalEmail());
    }

    @Test
    void rejectsInvalidEmails() {
        assertThat(emailAddressService.normalize(null)).isNull();
        assertThat(emailAddressService.normalize("")).isNull();
        assertThat(emailAddressService.normalize("not-an-email")).isNull();
        assertThat(emailAddressService.normalize("a@@gmail.com")).isNull();
    }
}
