package com.jobtrace.jobmarket.application;

import com.jobtrace.jobmarket.domain.SourceHealth;
import com.jobtrace.jobmarket.domain.SourceRegistration;
import com.jobtrace.jobmarket.domain.SourceUpdate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ManageJobMarketSources {

    private final SourceCatalogStore store;

    public ManageJobMarketSources(SourceCatalogStore store) {
        this.store = store;
    }

    public List<SourceHealth> list() {
        return store.list();
    }

    public Optional<SourceHealth> register(SourceRegistration registration) {
        return store.register(registration);
    }

    public Optional<SourceHealth> update(UUID sourceId, SourceUpdate update) {
        return store.update(sourceId, update);
    }
}
