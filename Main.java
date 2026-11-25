
import java.util.*;
import java.time.LocalDate;
import java.io.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;

public class Main {

    enum Prioridad { ALTA, MEDIA, BAJA }

    // PATRON STATE

    interface Estado extends Serializable {
        String nombre();
        Estado siguiente();
    }

    static class Pendiente implements Estado {
        public String nombre() {
            return "Pendiente";
        }
        public Estado siguiente() {
            return new EnProceso();
        }
    }

    static class EnProceso implements Estado {
        public String nombre() {
            return "En proceso";
        }
        public Estado siguiente() {
            return new EnRevision();
        }
    }

    static class EnRevision implements Estado {
        public String nombre() {
            return "En revisión";
        }
        public Estado siguiente() {
            return new Terminada();
        }
    }

    static class Terminada implements Estado {
        public String nombre() {
            return "Terminada";
        }
        public Estado siguiente() {
            return new Pendiente();
        }
    }

    // PATRON COMPOSITE

    static abstract class Tarea implements Serializable {

        String nombre, descripcion;
        LocalDate fecha;
        Prioridad prioridad;
        Estado estado = new Pendiente();

        Tarea(String n, String d, LocalDate f, Prioridad p) {
            editar(n, d, f, p);
        }

        void editar(String n, String d, LocalDate f, Prioridad p) {

            if (n.isBlank())
                throw new IllegalArgumentException("Escribe un nombre");

            nombre = n;
            descripcion = d;
            fecha = f;
            prioridad = p;
        }

        void avanzar() {
            estado = estado.siguiente();
        }

        abstract int total();

        abstract int hechas();

        List<Tarea> hijas() {
            return List.of();
        }

        int progreso() {
            return 100 * hechas() / total();
        }

        boolean atrasada() {
            return progreso() < 100
                && fecha.isBefore(LocalDate.now());
        }
    }

    static class TareaSimple extends Tarea {

        TareaSimple(String n, String d, LocalDate f, Prioridad p) {
            super(n, d, f, p);
        }

        int total() {
            return 1;
        }

        int hechas() {
            return estado instanceof Terminada ? 1 : 0;
        }
    }

    static class Proyecto extends Tarea {

        List<Tarea> subtareas = new ArrayList<>();

        Proyecto(String n, String d, LocalDate f, Prioridad p) {
            super(n, d, f, p);
        }

        List<Tarea> hijas() {
            return subtareas;
        }

        int total() {
            return subtareas.isEmpty() ? 1 :
                subtareas.stream().mapToInt(Tarea::total).sum();
        }

        int hechas() {

            return subtareas.isEmpty()
                ? (estado instanceof Terminada ? 1 : 0)
                : subtareas.stream().mapToInt(Tarea::hechas).sum();
        }
    }

    // PATRON STRATEGY

    interface Estrategia {
        Comparator<Tarea> comparador();
    }

    static class PorFecha implements Estrategia {

        public Comparator<Tarea> comparador() {
            return Comparator.comparing(t -> t.fecha);
        }
    }

    static class PorPrioridad implements Estrategia {

        public Comparator<Tarea> comparador() {
            return Comparator.comparing(t -> t.prioridad);
        }
    }

    static class PorNombre implements Estrategia {

        public Comparator<Tarea> comparador() {
            return Comparator.comparing(
                t -> t.nombre,
                String.CASE_INSENSITIVE_ORDER
            );
        }
    }

    // GESTOR DE TAREAS

    static class Gestor {

        List<Tarea> tareas = new ArrayList<>();

        Estrategia estrategia = new PorFecha();

        void agregar(Tarea t, Proyecto padre) {

            if (padre == null)
                tareas.add(t);
            else
                padre.subtareas.add(t);
        }

        void recorrer(List<Tarea> origen, List<Tarea> salida) {

            for (Tarea t : origen) {
                salida.add(t);
                recorrer(t.hijas(), salida);
            }
        }

        List<Tarea> listar(String texto) {

            List<Tarea> lista = new ArrayList<>();

            recorrer(tareas, lista);

            lista.removeIf(t ->
                !t.nombre.toLowerCase()
                    .contains(texto.toLowerCase())
            );

            lista.sort(estrategia.comparador());

            return lista;
        }

        void eliminar(Tarea t) {

            if (tareas.remove(t))
                return;

            for (Tarea p : listar("")) {

                if (p instanceof Proyecto &&
                    ((Proyecto)p).subtareas.remove(t))
                    return;
            }
        }

        void guardar() throws IOException {

            try (ObjectOutputStream o =
                new ObjectOutputStream(
                    new FileOutputStream("tareas.dat"))) {

                o.writeObject(tareas);
            }
        }

        @SuppressWarnings("unchecked")
        void cargar() throws IOException, ClassNotFoundException {

            if (!new File("tareas.dat").exists())
                return;

            try (ObjectInputStream i =
                new ObjectInputStream(
                    new FileInputStream("tareas.dat"))) {

                tareas = (List<Tarea>) i.readObject();
            }
        }
    }

    static Estrategia[] ordenes = {
        new PorFecha(),
        new PorPrioridad(),
        new PorNombre()
    };

    // INTERFAZ GRAFICA

    static void ventana(Gestor g) {

        JFrame v = new JFrame("TaskFlow | Gestor de tareas");

        DefaultTableModel m = new DefaultTableModel(
            new String[]{
                "Nombre", "Tipo", "Fecha",
                "Prioridad", "Estado", "Avance", "Atrasada"
            }, 0) {

            public boolean isCellEditable(int f, int c) {
                return false;
            }
        };

        JTable tabla = new JTable(m);
        tabla.setRowHeight(28);

        JLabel resumen = new JLabel();

        JTextField buscar = new JTextField(15);

        JComboBox<String> orden = new JComboBox<>(
            new String[]{"Fecha", "Prioridad", "Nombre"}
        );

        List<Tarea> visibles = new ArrayList<>();

        Runnable refrescar = () -> {

            g.estrategia = ordenes[orden.getSelectedIndex()];

            visibles.clear();
            visibles.addAll(g.listar(buscar.getText()));

            m.setRowCount(0);

            for (Tarea t : visibles) {

                m.addRow(new Object[]{
                    t.nombre,
                    t instanceof Proyecto ? "Proyecto" : "Tarea",
                    t.fecha,
                    t.prioridad,
                    t.estado.nombre(),
                    t.progreso() + "%",
                    t.atrasada() ? "Sí" : "No"
                });
            }

            int avance = g.tareas.isEmpty() ? 0 :
                100 * g.tareas.stream()
                    .mapToInt(Tarea::hechas).sum()
                / g.tareas.stream()
                    .mapToInt(Tarea::total).sum();

            resumen.setText(
                " TaskFlow | Actividades: "
                + g.listar("").size()
                + " | Avance: " + avance + "%"
            );
        };

        JPanel arriba = new JPanel();

        arriba.add(resumen);
        arriba.add(buscar);
        arriba.add(orden);

        JPanel botones = new JPanel();

        for (String accion : new String[]{
            "Nueva tarea",
            "Nuevo proyecto",
            "Subtarea",
            "Editar",
            "Cambiar estado",
            "Eliminar",
            "Buscar"
        }) {

            JButton b = new JButton(accion);

            botones.add(b);

            b.addActionListener(e -> {

                try {

                    int fila = tabla.getSelectedRow();

                    Tarea t = fila < 0
                        ? null : visibles.get(fila);

                    if (accion.equals("Buscar")) {
                        refrescar.run();
                        return;
                    }

                    if (accion.equals("Nueva tarea")
                        || accion.equals("Nuevo proyecto")
                        || accion.equals("Subtarea")
                        || accion.equals("Editar")) {

                        boolean editar = accion.equals("Editar");

                        if (editar && t == null)
                            throw new IllegalArgumentException(
                                "Selecciona una tarea"
                            );

                        if (accion.equals("Subtarea")
                            && !(t instanceof Proyecto))
                            throw new IllegalArgumentException(
                                "Selecciona un proyecto"
                            );

                        JTextField n = new JTextField(
                            editar ? t.nombre : ""
                        );

                        JTextField d = new JTextField(
                            editar ? t.descripcion : ""
                        );

                        JTextField f = new JTextField(
                            editar ? t.fecha.toString()
                            : LocalDate.now().toString()
                        );

                        JComboBox<Prioridad> p =
                            new JComboBox<>(Prioridad.values());

                        p.setSelectedItem(
                            editar ? t.prioridad : Prioridad.MEDIA
                        );

                        JPanel form = new JPanel(
                            new GridLayout(4, 2, 8, 8)
                        );

                        form.add(new JLabel("Nombre"));
                        form.add(n);

                        form.add(new JLabel("Descripción"));
                        form.add(d);

                        form.add(new JLabel("Fecha AAAA-MM-DD"));
                        form.add(f);

                        form.add(new JLabel("Prioridad"));
                        form.add(p);

                        if (JOptionPane.showConfirmDialog(
                            v, form, accion,
                            JOptionPane.OK_CANCEL_OPTION
                        ) != JOptionPane.OK_OPTION)
                            return;

                        LocalDate fecha =
                            LocalDate.parse(f.getText());

                        Prioridad pr =
                            (Prioridad)p.getSelectedItem();

                        if (editar) {

                            t.editar(
                                n.getText(),
                                d.getText(),
                                fecha,
                                pr
                            );

                        } else {

                            Tarea nueva =
                                accion.equals("Nuevo proyecto")
                                ? new Proyecto(
                                    n.getText(),
                                    d.getText(),
                                    fecha, pr
                                )
                                : new TareaSimple(
                                    n.getText(),
                                    d.getText(),
                                    fecha, pr
                                );

                            g.agregar(
                                nueva,
                                accion.equals("Subtarea")
                                    ? (Proyecto)t : null
                            );
                        }

                    } else {

                        if (t == null)
                            throw new IllegalArgumentException(
                                "Selecciona una actividad"
                            );

                        if (accion.equals("Cambiar estado")) {

                            t.avanzar();

                        } else if (
                            JOptionPane.showConfirmDialog(
                                v,
                                "¿Eliminar la actividad y sus subtareas?",
                                "Confirmar",
                                JOptionPane.YES_NO_OPTION
                            ) == JOptionPane.YES_OPTION
                        ) {

                            g.eliminar(t);
                        }
                    }

                    g.guardar();
                    refrescar.run();

                } catch (Exception ex) {

                    JOptionPane.showMessageDialog(
                        v, ex.getMessage()
                    );

                    refrescar.run();
                }
            });
        }

        orden.addActionListener(e -> refrescar.run());

        buscar.addActionListener(e -> refrescar.run());

        v.add(arriba, BorderLayout.NORTH);

        v.add(new JScrollPane(tabla));

        v.add(botones, BorderLayout.SOUTH);

        v.setSize(1050, 500);
        v.setLocationRelativeTo(null);
        v.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        refrescar.run();

        v.setVisible(true);
    }

    // VERSION POR CONSOLA

    static void consola(Gestor g) {

        Scanner s = new Scanner(System.in);

        while (true) {

            List<Tarea> lista = g.listar("");

            for (int i = 0; i < lista.size(); i++) {

                Tarea t = lista.get(i);

                System.out.println(
                    (i + 1) + ". " + t.nombre
                    + " | " + t.estado.nombre()
                    + " | " + t.prioridad
                    + " | " + t.fecha
                    + " | " + t.progreso() + "%"
                );
            }

            System.out.println("\n===== TASKFLOW =====");
            System.out.println("1. Crear tarea");
            System.out.println("2. Crear proyecto");
            System.out.println("3. Subtarea");
            System.out.println("4. Editar");
            System.out.println("5. Cambiar estado");
            System.out.println("6. Eliminar");
            System.out.println("7. Ordenar");
            System.out.println("0. Salir");

            if (!s.hasNextLine())
                return;

            String op = s.nextLine();

            if (op.equals("0"))
                return;

            try {

                Tarea t = null;

                if (List.of("3", "4", "5", "6").contains(op)) {

                    System.out.println("Número de actividad:");

                    t = lista.get(
                        Integer.parseInt(s.nextLine()) - 1
                    );
                }

                switch (op) {

                    case "1":
                    case "2":
                    case "3":
                    case "4":

                        if (op.equals("3")
                            && !(t instanceof Proyecto))
                            throw new IllegalArgumentException(
                                "Selecciona un proyecto"
                            );

                        System.out.println("Nombre:");
                        String n = s.nextLine();

                        System.out.println("Descripción:");
                        String d = s.nextLine();

                        System.out.println("Fecha AAAA-MM-DD:");

                        LocalDate f =
                            LocalDate.parse(s.nextLine());

                        System.out.println(
                            "Prioridad ALTA, MEDIA o BAJA:"
                        );

                        Prioridad p = Prioridad.valueOf(
                            s.nextLine().toUpperCase()
                        );

                        if (op.equals("4")) {

                            t.editar(n, d, f, p);

                        } else {

                            Tarea nueva = op.equals("2")
                                ? new Proyecto(n, d, f, p)
                                : new TareaSimple(n, d, f, p);

                            g.agregar(
                                nueva,
                                op.equals("3") ? (Proyecto)t : null
                            );
                        }

                        break;

                    case "5":

                        t.avanzar();
                        break;

                    case "6":

                        g.eliminar(t);
                        break;

                    case "7":

                        System.out.println(
                            "1. Fecha  2. Prioridad  3. Nombre"
                        );

                        g.estrategia = ordenes[
                            Integer.parseInt(s.nextLine()) - 1
                        ];

                        break;

                    default:

                        System.out.println("Opción inválida");
                }

                g.guardar();

            } catch (Exception ex) {

                System.out.println("Error: " + ex.getMessage());
            }
        }
    }

    // METODO PRINCIPAL

    public static void main(String[] args) throws Exception {

        Gestor g = new Gestor();

        g.cargar();

        if (args.length > 0 && args[0].equals("consola")) {

            consola(g);

        } else {

            SwingUtilities.invokeLater(() -> ventana(g));
        }
    }
}
