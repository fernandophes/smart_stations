package br.edu.ufersa.cc.seg.datacenter.services;

import java.util.List;

import br.edu.ufersa.cc.seg.datacenter.entities.Snapshot;
import br.edu.ufersa.cc.seg.datacenter.repositories.SnapshotRepository;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public class SnapshotService {

    private SnapshotRepository snapshotRepository = new SnapshotRepository();

    public Long countAll() {
        return snapshotRepository.countAll();
    }

    public List<Snapshot> listAll() {
        log.info("Listando todas as capturas...");
        return snapshotRepository.listAll();
    }

    public void create(final Snapshot snapshot) {
        snapshotRepository.create(snapshot);
        log.info("Captura cadastrada");
    }

}
