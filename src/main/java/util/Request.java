package util;

import java.io.Serializable;

/**
 * @author naison
 * @since 4/12/2020 21:07
 */
public class Request implements Serializable {
    private static final long serialVersionUID = 988750245807348185L;
    public int requestId;

    public Request() {
        this.requestId = 1;
    }
}
