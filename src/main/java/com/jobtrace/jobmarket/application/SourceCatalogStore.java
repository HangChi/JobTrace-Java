package com.jobtrace.jobmarket.application;

import com.jobtrace.jobmarket.domain.SourceHealth;
import com.jobtrace.jobmarket.domain.SourceRegistration;
import com.jobtrace.jobmarket.domain.SourceUpdate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceCatalogStore {

    List<SourceHealth> list();

    Optional<SourceHealth> register(SourceRegistration registration);

    Optional<SourceHealth> update(UUID sourceId, SourceUpdate update);
}
