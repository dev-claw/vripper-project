package me.vripper;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpringContext implements ApplicationContextAware {

  private static ConfigurableApplicationContext context;

  public static <T> T getBean(Class<T> beanClass) {
    return context.getBean(beanClass);
  }

  public static <T> Map<String, T> getBeansOfType(Class<T> beanClass) {
    return context.getBeansOfType(beanClass);
  }

  public static void close() {
    log.info("Application terminating...");
    if (context != null) {
      context.close();
    }
  }

  @Override
  public void setApplicationContext(ApplicationContext context) throws BeansException {

    // store ApplicationContext reference to access required beans later on
    SpringContext.context = ((ConfigurableApplicationContext) context);
  }
}
