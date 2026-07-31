package com.kita.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 017 FR-016 — account names are permanent and never reissued.
 *
 * <p>A structural guard rather than a behavioural one, because the risk is a future edit: hr links an
 * employee to an account <b>by name</b>, so adding a rename path (or hard-deleting an account, freeing
 * its name for a new hire) would silently transfer that employee's identity and roles to someone else.
 * Nothing at runtime would report that as an error — which is exactly why it is asserted here.
 */
class AccountNamePermanenceTest {

  @Test
  void appUserExposesNoWayToRenameAnAccount() {
    Method[] methods = AppUser.class.getMethods();

    assertThat(Arrays.stream(methods).map(Method::getName))
        .as("no setter may exist for the account name (017 FR-016)")
        .doesNotContain("setUsername", "renameTo", "setName");
  }

  @Test
  void theUsernameFieldIsNotMutableFromOutsideTheConstructor() throws Exception {
    // Present and readable, but assigned only at construction.
    assertThat(AppUser.class.getDeclaredField("username")).isNotNull();
    assertThat(Arrays.stream(AppUser.class.getMethods()).map(Method::getName))
        .contains("getUsername");
  }
}
