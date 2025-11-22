package br.edu.ufersa.cc.seg.common.network;

import java.io.Serializable;

import br.edu.ufersa.cc.seg.common.utils.Operation;
import lombok.Data;

@Data
public abstract class Message implements Serializable {

    private final Operation operation;

}
