package br.edu.ufersa.cc.seg.auth;

import java.util.UUID;

import br.edu.ufersa.cc.seg.common.utils.InstanceType;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class InstanceDto {

    private UUID id;
    private String identifier;
    private InstanceType type;

}
