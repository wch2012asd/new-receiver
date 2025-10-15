package com.example;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
@Data
@Accessors(chain = true)
public class NcBeanModel  implements Serializable {
    String variableName;
    String pngPath;
    String level;
    Long time;
}
