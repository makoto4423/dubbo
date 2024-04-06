package org.apache.dubbo.springboot.demo.consumer;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

// 应该是 alibaba 的 dubbo 有bug，在工作环境， 指定value会导致 filter不被启用，但 debug看到的ProtocolFilterWrapper是已经被织入进去
@Activate(group = CommonConstants.CONSUMER, value = "makoto")
public class ConsumerFilter implements Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        System.out.println("consumerFilter");
        return invoker.invoke(invocation);
    }
}
