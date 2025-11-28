package br.edu.ufersa.cc.seg.auth.services;

import java.util.List;
import java.util.Optional;

import br.edu.ufersa.cc.seg.auth.dto.InstanceDto;
import br.edu.ufersa.cc.seg.auth.entities.Instance;
import br.edu.ufersa.cc.seg.auth.repositories.InstanceRepository;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public class InstanceService {

    private InstanceRepository instanceRepository = new InstanceRepository();

    public Long countAll() {
        return instanceRepository.countAll();
    }

    public List<InstanceDto> listAll() {
        log.info("Listando todas as instâncias...");
        return instanceRepository.listAll().stream()
                .map(this::toDto)
                .toList();
    }

    public void create(final InstanceDto instance, final String secret) {
        instanceRepository.create(toEntity(instance).setSecret(secret));
    }

    public Optional<InstanceDto> getByIdentifierAndSecret(final String identifier, final String secret) {
        return instanceRepository.getByIdentifierAndSecret(identifier, secret)
                .map(this::toDto);
    }

    private InstanceDto toDto(final Instance entity) {
        return new InstanceDto()
                .setId(entity.getId())
                .setIdentifier(entity.getIdentifier())
                .setType(entity.getType());
    }

    private Instance toEntity(final InstanceDto dto) {
        return new Instance()
                .setId(dto.getId())
                .setIdentifier(dto.getIdentifier())
                .setType(dto.getType());
    }

}
