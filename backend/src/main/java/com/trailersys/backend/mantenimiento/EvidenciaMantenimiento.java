package com.trailersys.backend.mantenimiento;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name="mantenimiento_evidencias")
public class EvidenciaMantenimiento {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="mantenimiento_id",nullable=false) private Mantenimiento mantenimiento;
    @Column(nullable=false,length=30) private String categoria;
    @Column(nullable=false,length=255) private String nombre;
    @Column(nullable=false,length=100) private String tipoContenido;
    @Column(nullable=false) private long tamano;
    @Column(nullable=false) private LocalDateTime fechaCarga;
    @Basic(fetch=FetchType.LAZY) @Column(nullable=false, columnDefinition="bytea") private byte[] contenido;
    protected EvidenciaMantenimiento() {}
    public EvidenciaMantenimiento(Mantenimiento m,String c,String n,String t,byte[] b){mantenimiento=m;categoria=c;nombre=n;tipoContenido=t;contenido=b;tamano=b.length;fechaCarga=LocalDateTime.now();}
    public Long getId(){return id;} public Mantenimiento getMantenimiento(){return mantenimiento;} public String getCategoria(){return categoria;}
    public String getNombre(){return nombre;} public String getTipoContenido(){return tipoContenido;} public long getTamano(){return tamano;}
    public LocalDateTime getFechaCarga(){return fechaCarga;} public byte[] getContenido(){return contenido;}
}
