package util;

import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * @author naison
 * @since 3/15/2020 11:28
 */
@AllArgsConstructor
public class Response implements Serializable {
    private static final long serialVersionUID = -2641871072732556472L;
    public int requestId;
}
