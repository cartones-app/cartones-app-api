package com.eliasgonzalez.cartones.common.flags.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eliasgonzalez.cartones.common.flags.domain.FlagOverride;

@Repository
public interface FlagOverrideRepository extends JpaRepository<FlagOverride, String> {
}
