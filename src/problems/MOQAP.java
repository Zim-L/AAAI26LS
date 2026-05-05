package problems;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.StringTokenizer;

import core.AbstractIntegerPermutationProblem;
import core.JMetalRandom;
import core.PermutationSolution;


public class MOQAP extends AbstractIntegerPermutationProblem {

	int n;
	int M = 2;
	double weightLimit;
	double opt1, opt2;
	double[] x;
	double[] y;
	double[][] distance;

	private double[][][] flows; // flow matrix

	public MOQAP() {
		initProblem(50);
	}

	public MOQAP(int n) {
		initProblem(n);
	}

	public void initProblem(int n) {
		this.n = n;
		setNumberOfVariables(n);
		setNumberOfObjectives(M);
		setName("QAP-" + n);

		JMetalRandom random = JMetalRandom.getInstance();
		x = new double[n];
		y = new double[n];
		for (int i = 0; i < n; i++) {
			x[i] = random.nextDouble(0, 5000);
			y[i] = random.nextDouble(0, 5000);
		}
		// calculate distance matrix
		distance = new double[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = i; j < n; j++) {
				distance[i][j] = Math.sqrt((x[i] - x[j]) * (x[i] - x[j]) + (y[i] - y[j]) * (y[i] - y[j]));
				distance[j][i] = distance[i][j];
			}
		}

		// create flow matrix
		flows = new double[M][n][n];
		for (int m = 0; m < M; m++) {
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					flows[m][i][j] = random.nextDouble(0, 100);
				}
			}
		}
	}

	@Override
	public int getLength() {
		return n;
	}

	public void save(String path) {
		File file = new File(path);
		if (file.exists()) {
			System.out.println("Save failed, file exists");
			return;
		}
		try {
			PrintWriter writer = new PrintWriter(file);
			writer.println(n);
			writer.println(M);
			// save distance
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					writer.print(distance[i][j] + " ");
				}
				writer.println();
			}

			// save matrix
			for (int m = 0; m < M; m++) {
				for (int i = 0; i < n; i++) {
					for (int j = 0; j < n; j++) {
						writer.print(flows[m][i][j] + " ");
					}
					writer.println();
				}
			}
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public MOQAP load(String path) {
	    try (BufferedReader reader = new BufferedReader(new FileReader(path))) {

	        // Read the problem dimensions
	        this.n = Integer.parseInt(reader.readLine());
	        this.M = Integer.parseInt(reader.readLine());
	        setNumberOfVariables(n);
	        setNumberOfObjectives(M);

	        // Read the distance matrix (n × n)
	        for (int i = 0; i < n; i++) {
	            String line = reader.readLine();
	            StringTokenizer tok = new StringTokenizer(line);

	            for (int j = 0; j < n; j++) {
	                distance[i][j] = Double.parseDouble(tok.nextToken());
	            }
	        }

	        // Read M flow matrices (each n × n)
	        for (int m = 0; m < M; m++) {
	            for (int i = 0; i < n; i++) {
	                String line = reader.readLine();
	                StringTokenizer tok = new StringTokenizer(line);

	                for (int j = 0; j < n; j++) {
	                    flows[m][i][j] = Double.parseDouble(tok.nextToken());
	                }
	            }
	        }

	    } catch (IOException e) {
	        e.printStackTrace();
	    }

	    System.out.println(path);
	    return this;
	}

	public MOQAP loadXiaofeng(String path) {
	    int m = getNumberOfObjectives();
	    int n = getNumberOfVariables();

	    String afile = Paths.get(path, "mQAP-M" + m + "-D" + n + "_a.txt").toString();
	    String b1file = Paths.get(path, "mQAP-M" + m + "-D" + n + "_b1.txt").toString();
	    String b2file = Paths.get(path, "mQAP-M" + m + "-D" + n + "_b2.txt").toString();

	    // Read distance matrix
	    try (BufferedReader reader = new BufferedReader(new FileReader(afile))) {
	        for (int i = 0; i < n; i++) {
	            String line = reader.readLine();
	            if (line == null) throw new IOException("Unexpected end of file: " + afile);
	            String[] tokens = line.trim().split("\\s+");
	            for (int j = 0; j < n; j++) {
	                distance[i][j] = Double.parseDouble(tokens[j]);
	            }
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }

	    // Read flow matrices
	    try (
	        BufferedReader reader1 = new BufferedReader(new FileReader(b1file));
	        BufferedReader reader2 = new BufferedReader(new FileReader(b2file))
	    ) {
	        for (int i = 0; i < n; i++) {
	            String line1 = reader1.readLine();
	            String line2 = reader2.readLine();
	            if (line1 == null || line2 == null) throw new IOException("Unexpected end of file in flow files.");

	            String[] f1 = line1.trim().split("\\s+");
	            String[] f2 = line2.trim().split("\\s+");
	            for (int j = 0; j < n; j++) {
	                flows[0][i][j] = Double.parseDouble(f1[j]);
	                flows[1][i][j] = Double.parseDouble(f2[j]);
	            }
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }

	    return this;
	}

	@Override
	public PermutationSolution<Integer> evaluate(PermutationSolution<Integer> solution) {
	    final int n       = solution.variables().size();
	    final int M       = flows.length;
	    final double[][]  distance = this.distance;
	    final double[][][] flows    = this.flows;
	    final int[] perm = new int[n];
	    for (int i = 0; i < n; i++) {
	        perm[i] = solution.variables().get(i);
	    }
	    final double[] objectives = solution.objectives();

	    Arrays.fill(objectives, 0.0);

	    for (int k = 0; k < M; k++) {
	        double cost = 0.0;
	        final double[][] flowK = flows[k];
	        for (int i = 0; i < n; i++) {
	            final int pi = perm[i];
	            final double[] distI = distance[i];
	            final double[] flowPi = flowK[pi];
	            for (int j = 0; j < n; j++) {
	                cost += distI[j] * flowPi[perm[j]];
	            }
	        }
	        objectives[k] = cost;
	    }

	    return solution;
	}

	public static void main(String[] args) {
		int[] ns = {100, 500, 1000, 5000};
		for (int n : ns) {
			var problem = new MOQAP(n);
			problem.save("D:/GECCO26scale/Data/"+problem.getName()+".txt");
		}
	}
}
