// SPDX-License-Identifier: Apache-2.0

package {{packagePath}};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** KeelBase governed AI tools skeleton 入口。 */
@SpringBootApplication
public class {{Domain}}Application {

    public static void main(String[] args) {
        SpringApplication.run({{Domain}}Application.class, args);
    }
}
