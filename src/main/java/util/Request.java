package util;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author naison
 * @since 4/12/2020 21:07
 */
@Getter
@Setter
public class Request implements Serializable {
    private static final AtomicInteger AI = new AtomicInteger(1);
    private static final long serialVersionUID = 988750245807348185L;
    public int requestId;

    public Request() {
        this.requestId = AI.getAndIncrement();
    }
}
