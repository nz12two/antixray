package com.antixray.plugin.folia;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Adaptador para compatibilidade com Folia (region-based threading do Paper).
 * <p>
 * Detecta automaticamente se o servidor é Folia via reflection
 * (presença da classe {@code io.papermc.paper.threadedregions.RegionizedServer})
 * e redireciona as chamadas de scheduler para a implementação correta.
 * </p>
 * 
 * <p>Em servidores não-Folia (Paper/Spigot/Purpur etc.), usa o Bukkit Scheduler tradicional.</p>
 * 
 * <p>O cache do resultado de {@link #isFolia()} é feito em um bloco estático sincronizado
 * para evitar chamadas repetidas de reflection.</p>
 */
public class FoliaAdapter {

    private static volatile boolean folia = false;
    private static volatile boolean checked = false;

    // === Reflection caches para a API do Folia ===
    // Bukkit methods
    private static Method getGlobalRegionSchedulerMethod;
    private static Method getAsyncSchedulerMethod;

    // GlobalRegionScheduler methods
    private static Method globalRun;
    private static Method globalRunDelayed;
    private static Method globalRunAtFixedRate;
    private static Method globalIsCurrentThread;

    // AsyncScheduler methods
    private static Method asyncRunNow;
    private static Method asyncRunAtFixedRate;

    // Entity scheduler methods
    private static Method entityGetSchedulerMethod;
    private static Method entitySchedulerRun;

    // Instâncias cacheadas dos schedulers (Folia only)
    private static Object globalRegionScheduler;
    private static Object asyncScheduler;

    static {
        checkFolia();
    }

    // ---------------------------------------------------------------
    //  Inner class: ScheduledTask
    // ---------------------------------------------------------------

    /**
     * Wrapper unificado para tarefas agendadas.
     * <p>
     * Em servidores Folia, encapsula
     * {@code io.papermc.paper.threadedregions.scheduler.ScheduledTask}.<br>
     * Em servidores Paper/Spigot, encapsula {@code org.bukkit.scheduler.BukkitTask}.
     * </p>
     */
    public static final class ScheduledTask {

        private final Object task;
        private final boolean foliaTask;

        ScheduledTask(Object task, boolean foliaTask) {
            this.task = task;
            this.foliaTask = foliaTask;
        }

        /**
         * Cancela a tarefa agendada.
         * Funciona tanto em Folia quanto em Paper/Spigot.
         * É seguro chamar múltiplas vezes ou com tarefa nula.
         */
        public void cancel() {
            if (task == null) return;
            try {
                if (foliaTask) {
                    // Folia's ScheduledTask tem o método cancel()
                    Method cancelMethod = task.getClass().getMethod("cancel");
                    cancelMethod.invoke(task);
                } else {
                    ((BukkitTask) task).cancel();
                }
            } catch (Exception ignored) {
                // Silenciosamente ignora erros de cancelamento
            }
        }

        /**
         * Retorna o objeto interno da tarefa (para uso avançado).
         *
         * @return O objeto BukkitTask ou Folia ScheduledTask bruto
         */
        public Object getRaw() {
            return task;
        }
    }

    // ---------------------------------------------------------------
    //  Inicialização / Detecção de Folia
    // ---------------------------------------------------------------

    /**
     * Detecta se o servidor é Folia e, em caso positivo, faz cache dos
     * métodos de reflection necessários para chamar a API de schedulers do Folia.
     * <p>
     * Usa double-checked locking com campos volatile para segurança em
     * cenários multi-thread (comum em Bukkit).
     * </p>
     */
    private static void checkFolia() {
        if (checked) return;
        synchronized (FoliaAdapter.class) {
            if (checked) return;
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                folia = true;
                cacheFoliaMethods();
            } catch (ClassNotFoundException e) {
                folia = false;
            }
            checked = true;
        }
    }

    /**
     * Faz cache via reflection de todos os métodos da API Folia necessários
     * para o funcionamento da classe.
     * <p>
     * Se qualquer passo falhar, o servidor é tratado como não-Folia
     * (degradação graciosa).
     * </p>
     */
    private static void cacheFoliaMethods() {
        try {
            Class<?> globalRegionSchedulerClass = Class.forName(
                    "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Class<?> asyncSchedulerClass = Class.forName(
                    "io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            Class<?> entitySchedulerClass = Class.forName(
                    "io.papermc.paper.threadedregions.scheduler.EntityScheduler");

            // Bukkit.getGlobalRegionScheduler() / getAsyncScheduler()
            getGlobalRegionSchedulerMethod = Bukkit.class.getMethod("getGlobalRegionScheduler");
            getAsyncSchedulerMethod = Bukkit.class.getMethod("getAsyncScheduler");

            globalRegionScheduler = getGlobalRegionSchedulerMethod.invoke(null);
            asyncScheduler = getAsyncSchedulerMethod.invoke(null);

            // GlobalRegionScheduler methods
            globalRun = globalRegionSchedulerClass.getMethod("run", Plugin.class, Consumer.class);
            globalRunDelayed = globalRegionSchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            globalRunAtFixedRate = globalRegionSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            globalIsCurrentThread = globalRegionSchedulerClass.getMethod("isCurrentThread");

            // AsyncScheduler methods
            asyncRunNow = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);
            asyncRunAtFixedRate = asyncSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

            // Entity.getScheduler() / EntityScheduler.run()
            entityGetSchedulerMethod = Entity.class.getMethod("getScheduler");
            entitySchedulerRun = entitySchedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class, long.class);

        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[FoliaAdapter] Failed to cache Folia methods, falling back to non-Folia mode", e);
            folia = false;
        }
    }

    /**
     * Cria um {@link Consumer} que ignora o argumento e delega ao Runnable.
     * <p>
     * Necessário porque a API do Folia usa {@code Consumer<ScheduledTask>}
     * em vez de {@code Runnable}.
     * </p>
     */
    @SuppressWarnings("unchecked")
    private static <T> Consumer<T> toConsumer(Runnable task) {
        return ignored -> task.run();
    }

    // ---------------------------------------------------------------
    //  API Pública
    // ---------------------------------------------------------------

    /**
     * Verifica se o servidor atual é Folia.
     * <p>
     * O resultado é verificado uma única vez (no primeiro acesso ou no
     * carregamento da classe) e cacheado para sempre.
     * </p>
     *
     * @return {@code true} se o servidor for Folia, {@code false} caso contrário
     */
    public static boolean isFolia() {
        if (!checked) checkFolia();
        return folia;
    }

    /**
     * Executa uma tarefa de forma síncrona.
     * <ul>
     *   <li><b>Folia:</b> {@code Bukkit.getGlobalRegionScheduler().run()}</li>
     *   <li><b>Paper/Spigot:</b> {@code Bukkit.getScheduler().runTask()}</li>
     * </ul>
     *
     * @param plugin O plugin proprietário da tarefa
     * @param task   A tarefa a ser executada
     * @return {@link ScheduledTask} encapsulando a tarefa, ou {@code null} em caso de erro
     */
    public static ScheduledTask runTask(Plugin plugin, Runnable task) {
        if (isFolia()) {
            try {
                Consumer<Object> consumer = toConsumer(task);
                Object result = globalRun.invoke(globalRegionScheduler, plugin, consumer);
                return new ScheduledTask(result, true);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING,
                        "[FoliaAdapter] Failed to run sync task on Folia scheduler", e);
                return null;
            }
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, task);
            return new ScheduledTask(bukkitTask, false);
        }
    }

    /**
     * Executa uma tarefa de forma assíncrona.
     * <ul>
     *   <li><b>Folia:</b> {@code Bukkit.getAsyncScheduler().runNow()}</li>
     *   <li><b>Paper/Spigot:</b> {@code Bukkit.getScheduler().runTaskAsynchronously()}</li>
     * </ul>
     *
     * @param plugin O plugin proprietário da tarefa
     * @param task   A tarefa a ser executada
     * @return {@link ScheduledTask} encapsulando a tarefa, ou {@code null} em caso de erro
     */
    public static ScheduledTask runTaskAsynchronously(Plugin plugin, Runnable task) {
        if (isFolia()) {
            try {
                Consumer<Object> consumer = toConsumer(task);
                Object result = asyncRunNow.invoke(asyncScheduler, plugin, consumer);
                return new ScheduledTask(result, true);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING,
                        "[FoliaAdapter] Failed to run async task on Folia scheduler", e);
                return null;
            }
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return new ScheduledTask(bukkitTask, false);
        }
    }

    /**
     * Executa uma tarefa síncrona repetidamente.
     * <ul>
     *   <li><b>Folia:</b> {@code Bukkit.getGlobalRegionScheduler().runAtFixedRate()}</li>
     *   <li><b>Paper/Spigot:</b> {@code Bukkit.getScheduler().runTaskTimer()}</li>
     * </ul>
     * <p>
     * O atraso e o período são em <b>ticks</b> (1 tick = 1/20 de segundo).
     </p>
     *
     * @param plugin O plugin proprietário da tarefa
     * @param task   A tarefa a ser executada
     * @param delay  Atraso inicial em ticks
     * @param period Período entre execuções em ticks (deve ser &gt; 0)
     * @return {@link ScheduledTask} encapsulando a tarefa, ou {@code null} em caso de erro
     */
    public static ScheduledTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        if (isFolia()) {
            try {
                Consumer<Object> consumer = toConsumer(task);
                Object result = globalRunAtFixedRate.invoke(globalRegionScheduler, plugin, consumer, delay, period);
                return new ScheduledTask(result, true);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING,
                        "[FoliaAdapter] Failed to run sync timer task on Folia scheduler", e);
                return null;
            }
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            return new ScheduledTask(bukkitTask, false);
        }
    }

    /**
     * Executa uma tarefa assíncrona repetidamente.
     * <ul>
     *   <li><b>Folia:</b> {@code Bukkit.getAsyncScheduler().runAtFixedRate()}
     *       (converte ticks → milissegundos: 1 tick = 50 ms)</li>
     *   <li><b>Paper/Spigot:</b> {@code Bukkit.getScheduler().runTaskTimerAsynchronously()}</li>
     * </ul>
     * <p>
     * O atraso e o período são em <b>ticks</b> (1 tick ≈ 50 ms).
     * Em Folia, os valores são convertidos para milissegundos.
     * </p>
     *
     * @param plugin O plugin proprietário da tarefa
     * @param task   A tarefa a ser executada
     * @param delay  Atraso inicial em ticks
     * @param period Período entre execuções em ticks (deve ser &gt; 0)
     * @return {@link ScheduledTask} encapsulando a tarefa, ou {@code null} em caso de erro
     */
    public static ScheduledTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) {
        if (isFolia()) {
            try {
                Consumer<Object> consumer = toConsumer(task);
                // AsyncScheduler do Folia usa TimeUnit, convertemos ticks → ms
                Object result = asyncRunAtFixedRate.invoke(asyncScheduler, plugin, consumer,
                        delay * 50L, period * 50L, TimeUnit.MILLISECONDS);
                return new ScheduledTask(result, true);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING,
                        "[FoliaAdapter] Failed to run async timer task on Folia scheduler", e);
                return null;
            }
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
            return new ScheduledTask(bukkitTask, false);
        }
    }

    /**
     * Executa uma tarefa na <b>região da entidade</b> (exclusivo Folia).
     * <p>
     * Em servidores Folia, usa {@code Entity.getScheduler().run()} para garantir
     * que a tarefa seja executada na thread responsável pela região onde a
     * entidade está carregada.
     * </p>
     * <p>
     * Em servidores Paper/Spigot, executa como uma task síncrona normal via
     * {@code Bukkit.getScheduler().runTask()}.
     * </p>
     *
     * @param plugin O plugin proprietário da tarefa
     * @param entity A entidade alvo (não pode ser nula)
     * @param task   A tarefa a ser executada na região da entidade
     */
    public static void runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (isFolia()) {
            try {
                Object entityScheduler = entityGetSchedulerMethod.invoke(entity);
                Consumer<Object> consumer = toConsumer(task);
                entitySchedulerRun.invoke(entityScheduler, plugin, consumer, (Runnable) null, 0L);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING,
                        "[FoliaAdapter] Failed to run entity task on Folia scheduler, falling back to global", e);
                // Fallback gracioso: tenta executar na thread global
                runTask(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Cancela uma tarefa agendada.
     * <p>
     * Funciona tanto para tarefas Folia quanto Paper/Spigot.
     * É seguro chamar com {@code null}.
     * </p>
     *
     * @param task A tarefa a ser cancelada (pode ser {@code null})
     */
    public static void cancelTask(ScheduledTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Verifica se o código atual está sendo executado na <b>thread global</b>
     * (Folia) ou na <b>thread principal</b> (Paper/Spigot).
     * <ul>
     *   <li><b>Folia:</b> {@code Bukkit.getGlobalRegionScheduler().isCurrentThread()}</li>
     *   <li><b>Paper/Spigot:</b> {@code Bukkit.isPrimaryThread()}</li>
     * </ul>
     *
     * @return {@code true} se estiver na thread global/principal
     */
    public static boolean isGlobalThread() {
        if (isFolia()) {
            try {
                return (boolean) globalIsCurrentThread.invoke(globalRegionScheduler);
            } catch (Exception e) {
                return false;
            }
        } else {
            return Bukkit.isPrimaryThread();
        }
    }

    /**
     * Garante que a tarefa seja executada na <b>thread global</b> (Folia) ou
     * <b>principal</b> (Paper/Spigot).
     * <p>
     * Se já estiver na thread correta, executa imediatamente.
     * Caso contrário, agenda a execução via {@link #runTask(Plugin, Runnable)}.
     * </p>
     *
     * @param plugin O plugin proprietário da tarefa
     * @param task   A tarefa a ser executada na thread global/principal
     */
    public static void ensureGlobal(Plugin plugin, Runnable task) {
        if (isGlobalThread()) {
            task.run();
        } else {
            runTask(plugin, task);
        }
    }

    /**
     * Construtor privado para evitar instanciação da classe utilitária.
     */
    private FoliaAdapter() {
        throw new UnsupportedOperationException("Utility class – do not instantiate");
    }
}
