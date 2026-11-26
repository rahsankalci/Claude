package com.garantibbva.processarchitecture.converter.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Avrasya (Oracle Workflow tasarimi) XML'inin bellek modeli. */
public final class AvrasyaProcess {
    public String name = "";
    public String description = "";
    public String documentation = "";
    public String aclId = "";
    public String categoryId = "";
    public String containerId = "";
    /** RefBranchNum, RefCustomerNum... -> container yolu (surec anahtari). */
    public final Map<String, String> attributes = new LinkedHashMap<>();
    public Scope root = new Scope("");

    /** Bir surec ya da blok icindeki aktiviteler + baglantilar. */
    public static final class Scope {
        public final String name;
        public final List<Act> acts = new ArrayList<>();
        public final List<Conn> conns = new ArrayList<>();
        /** XML'de baglanti yoksa (kesik dosya) true: akis mapping'lerden cikarildi. */
        public boolean connectionsInferred;

        public Scope(String name) {
            this.name = name;
        }

        public Act byIndex(String idx) {
            for (Act a : acts) {
                if (a.index.equals(idx)) {
                    return a;
                }
            }
            return null;
        }

        public Act start() {
            for (Act a : acts) {
                if (a.isStart()) {
                    return a;
                }
            }
            return null;
        }
    }

    public static final class Act {
        public String name = "";
        public String type = "";
        public String index = "";
        public String programId = "";
        public String startOption = "";
        public String startCondition = "";
        public String endCondition = "TRUE";
        public String wqOption = "";
        public String wqId = "";
        public String wqFromContainer = "";
        public String description = "";
        public String documentation = "";
        public int incoming;
        public int left = -1;
        public int top = -1;
        public final List<Mapping> maps = new ArrayList<>();
        /** Yalnizca ActivityType=Block icin. */
        public Scope block;

        public boolean isStart() {
            return "Start".equalsIgnoreCase(type);
        }

        public boolean isEnd() {
            return "End".equalsIgnoreCase(type);
        }

        public boolean isBlock() {
            return "Block".equalsIgnoreCase(type) || block != null;
        }

        /** StartOption=M -> insan gorevi; A -> otomatik servis. */
        public boolean isManual() {
            return "M".equalsIgnoreCase(startOption);
        }
    }

    public static final class Mapping {
        /** NDM (girdi), DDC/DLC (cikti). */
        public final String type;
        public final String srcActivityId;
        public final String srcActivity;
        public final String srcNode;
        public final String dstNode;
        /** F=alan kopyala, S=sabit deger, C=birlestir (a+b). */
        public final String valueType;

        public Mapping(String type, String srcActivityId, String srcActivity, String srcNode, String dstNode, String valueType) {
            this.type = type;
            this.srcActivityId = srcActivityId;
            this.srcActivity = srcActivity;
            this.srcNode = srcNode;
            this.dstNode = dstNode;
            this.valueType = valueType;
        }

        public boolean isInput() {
            return "NDM".equalsIgnoreCase(type);
        }
    }

    public static final class Conn {
        public final String src;
        public final String dst;
        public final String condition;
        public final String description;

        public Conn(String src, String dst, String condition, String description) {
            this.src = src;
            this.dst = dst;
            this.condition = condition == null || condition.isBlank() ? "TRUE" : condition.trim();
            this.description = description == null ? "" : description.trim();
        }
    }
}
