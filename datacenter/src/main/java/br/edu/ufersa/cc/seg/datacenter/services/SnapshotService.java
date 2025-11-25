package br.edu.ufersa.cc.seg.datacenter.services;

import java.time.LocalDateTime;
import java.util.List;

import br.edu.ufersa.cc.seg.common.dto.SnapshotDto;
import br.edu.ufersa.cc.seg.common.utils.Constants;
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

    public List<SnapshotDto> listAll() {
        log.info("Listando todas as capturas...");
        return snapshotRepository.listAll().stream()
                .map(this::toDto)
                .toList();
    }

    public List<SnapshotDto> listAllAfter(final LocalDateTime timestamp) {
        log.info("Listando todas as capturas...");
        return snapshotRepository.listAllAfter(timestamp).stream()
                .map(this::toDto)
                .toList();
    }

    public void create(final Snapshot snapshot) {
        snapshotRepository.create(snapshot);
        log.info("Captura cadastrada");
    }

    private SnapshotDto toDto(final Snapshot entity) {
        return new SnapshotDto()
                .setId(entity.getId())
                .setDeviceName(entity.getDeviceName())
                .setElement(entity.getElement())
                .setCapturedValue(entity.getCapturedValue())
                .setTimestamp(entity.getTimestamp().format(Constants.DATE_TIME_FORMATTER));
    }

}
