ARG ARCH=amd64
ARG REGISTRY=registry.cn-hongkong.aliyuncs.com
ARG VINEYARD_VERSION=latest
FROM $REGISTRY/graphscope/graphscope-dev:$VINEYARD_VERSION-$ARCH as builder

ARG CI=false
ARG ENABLE_COORDINATOR=false

ARG profile=debug
ENV profile=$profile

COPY --chown=graphscope:graphscope . /home/graphscope/graphscope

COPY --chown=graphscope:graphscope ./interactive_engine/assembly/src/conf/maven.settings.xml /home/graphscope/.m2/settings.xml

USER graphscope
RUN rustup toolchain install 1.87.0 && rustup default 1.87.0

RUN cd /home/graphscope/graphscope \
    && . ~/.graphscope_env \
    && cd /home/graphscope/graphscope/interactive_engine \
    && mvn clean package -P groot -DskipTests --quiet -Drust.compile.mode="$profile" \
    && tar xzf /home/graphscope/graphscope/interactive_engine/assembly/target/groot.tar.gz -C /home/graphscope/

# build coordinator
RUN if [ "${ENABLE_COORDINATOR}" = "true" ]; then \
      cd /home/graphscope/graphscope/flex/coordinator \
      && python3 setup.py bdist_wheel \
      && mkdir -p /home/graphscope/groot/wheel \
      && cp dist/*.whl /home/graphscope/groot/wheel; \
    fi

FROM ubuntu:22.04

ARG ENABLE_COORDINATOR=false

ENV DEBIAN_FRONTEND=noninteractive

# shanghai zoneinfo
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo '$TZ' > /etc/timezone

RUN apt-get update -y && \
    apt-get install -y sudo default-jdk dnsutils tzdata lsof \
        libjemalloc-dev libunwind-dev binutils less vim && \
    apt-get clean -y && \
    rm -rf /var/lib/apt/lists/*

ENV GRAPHSCOPE_HOME=/usr/local
ENV JAVA_HOME=/usr/lib/jvm/default-java

COPY --from=builder /home/graphscope/groot /usr/local/groot

RUN useradd -m graphscope -u 1001 \
    && echo 'graphscope ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers
RUN sudo chmod a+wrx /tmp

# install coordinator
RUN if [ "${ENABLE_COORDINATOR}" = "true" ]; then \
      apt-get update -y && apt-get install -y python3-pip && \
      apt-get clean -y && rm -rf /var/lib/apt/lists/* && \
      pip3 install --upgrade pip && \
      pip3 install /usr/local/groot/wheel/*.whl; \
    fi

USER graphscope
WORKDIR /home/graphscope

ENV PATH=${PATH}:/home/graphscope/.local/bin
ENV SOLUTION=GRAPHSCOPE_INSIGHT

# init log directory
RUN sudo mkdir /var/log/graphscope \
  && sudo chown -R $(id -u):$(id -g) /var/log/graphscope
