package com.eliasgonzalez.cartones.security.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, String> {
}
