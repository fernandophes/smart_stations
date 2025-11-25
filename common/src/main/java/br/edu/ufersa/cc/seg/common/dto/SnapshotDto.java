package br.edu.ufersa.cc.seg.common.dto;

import java.util.UUID;

import br.edu.ufersa.cc.seg.common.utils.Element;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SnapshotDto {

    private UUID id;
    private String deviceName;
    private String timestamp;
    private Element element;
    private double capturedValue;

}
